/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.component.model;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributeValue;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.attributes.CompatibilityRuleChainInternal;
import org.gradle.api.internal.attributes.DisambiguationRuleChainInternal;
import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentAttributeMatcher {
    public static final Function<Object, AttributeValue<Object>> CANDIDATE_VALUES_TRANSFORM = new Function<Object, AttributeValue<Object>>() {
        @Override
        public AttributeValue<Object> apply(Object input) {
            return AttributeValue.of(input);
        }
    };
    private final AttributesSchema consumerAttributeSchema;
    private final AttributesSchema producerAttributeSchema;
    private final Map<HasAttributes, MatchDetails> matchDetails = Maps.newHashMap();
    private final AttributeContainer requestedAttributesContainer;

    public ComponentAttributeMatcher(AttributesSchema consumerAttributeSchema, AttributesSchema producerAttributeSchema,
                                     Iterable<HasAttributes> candidates, //configAttributes + artifactAttributes
                                     AttributeContainer requestedAttributesContainer) {
        this.consumerAttributeSchema = consumerAttributeSchema;
        this.producerAttributeSchema = producerAttributeSchema;
        Set<Attribute<?>> requestedAttributeSet = requestedAttributesContainer.keySet();
        for (HasAttributes cand : candidates) {
            boolean hasAllAttributes = cand.getAttributes().keySet().containsAll(requestedAttributeSet);
            matchDetails.put(cand, new MatchDetails(hasAllAttributes));
        }
        this.requestedAttributesContainer = requestedAttributesContainer;
        doMatch();
    }

    private void doMatch() {
        Set<Attribute<Object>> requestedAttributes = Cast.uncheckedCast(requestedAttributesContainer.keySet());
        for (Map.Entry<HasAttributes, MatchDetails> entry : matchDetails.entrySet()) {
            HasAttributes key = entry.getKey();
            MatchDetails details = entry.getValue();
            AttributeContainer dependencyAttributesContainer = key.getAttributes();
            Set<Attribute<Object>> dependencyAttributes = Cast.uncheckedCast(dependencyAttributesContainer.keySet());
            Set<Attribute<Object>> commonAttributes = Sets.intersection(requestedAttributes, dependencyAttributes);
            for (Attribute<Object> attribute : commonAttributes) {
                AttributeMatchingStrategy<Object> strategy = Cast.uncheckedCast(consumerAttributeSchema.getMatchingStrategy(attribute));
                try {
                    details.update(attribute, strategy, requestedAttributesContainer.getAttribute(attribute), dependencyAttributesContainer.getAttribute(attribute));
                } catch (Exception ex) {
                    throw new GradleException("Unexpected error thrown when trying to match attribute values with " + strategy, ex);
                }
            }
        }
    }

    public List<? extends HasAttributes> getFullMatchs() {
        List<HasAttributes> matchs = new ArrayList<HasAttributes>(1);
        for (Map.Entry<HasAttributes, MatchDetails> entry : matchDetails.entrySet()) {
            MatchDetails details = entry.getValue();
            if (details.isFullMatch && details.hasAllAttributes) {
                matchs.add(entry.getKey());
            }
        }
        return disambiguateUsingClosestMatch(matchs);
    }

    public boolean hasFailingMatches() {
        for (Map.Entry<HasAttributes, MatchDetails> entry : matchDetails.entrySet()) {
            MatchDetails details = entry.getValue();
            if (details.failure) {
                return true;
            }
        }
        return false;
    }

    private List<HasAttributes> disambiguateUsingClosestMatch(List<HasAttributes> matchs) {
        if (matchs.size() > 1) {
            List<HasAttributes> remainingMatches = selectClosestMatches(matchs);
            if (remainingMatches != null) {
                return disambiguateUsingProducerSchema(remainingMatches);
            }
        }
        return matchs;
    }

    private List<HasAttributes> disambiguateUsingProducerSchema(List<HasAttributes> matchs) {
        if (matchs.size() < 2 || producerAttributeSchema == null) {
            return matchs;
        }
        // If we are reaching this point, it means that we have more than one match, so we need to
        // ask the producer if it has any preference: so far only the consumer schema was used. Now
        // we need to take into consideration the producer schema
        Set<Attribute<?>> producerOnlyAttributes = Sets.newHashSet();
        for (HasAttributes match : matchs) {
            AttributeContainer attributes = match.getAttributes();
            for (Attribute<?> attribute : attributes.keySet()) {
                if (!requestedAttributesContainer.contains(attribute)) {
                    producerOnlyAttributes.add(attribute);
                }
            }
        }
        Set<Attribute<?>> consumerAttributes = consumerAttributeSchema.getAttributes();
        List<HasAttributes> remainingMatches = Lists.newArrayList(matchs);
        final Map<HasAttributes, Object> values = Maps.newHashMap();
        for (Attribute<?> attribute : producerOnlyAttributes) {
            for (HasAttributes match : matchs) {
                Object maybeProvided = match.getAttributes().getAttribute(attribute);
                if (maybeProvided != null) {
                    values.put(match, maybeProvided);
                }
            }
            if (!values.isEmpty()) {
                AttributeMatchingStrategy<Object> matchingStrategy = Cast.uncheckedCast(producerAttributeSchema.getMatchingStrategy(attribute));
                final AttributeValue<Object> absent = consumerAttributes.contains(attribute) ? AttributeValue.missing() : AttributeValue.unknown();
                final List<HasAttributes> best = new ArrayList<HasAttributes>();
                MultipleCandidatesDetails<Object> details = new MultipleCandidatesDetails<Object>() {
                    @Override
                    public AttributeValue<Object> getConsumerValue() {
                        return absent;
                    }

                    @Override
                    public Map<HasAttributes, AttributeValue<Object>> getCandidateValues() {
                        return Maps.transformValues(values, CANDIDATE_VALUES_TRANSFORM);
                    }

                    @Override
                    public void closestMatch(HasAttributes key) {
                        best.add(key);
                    }
                };
                DisambiguationRuleChainInternal<Object> disambiguationRules = (DisambiguationRuleChainInternal<Object>) matchingStrategy.getDisambiguationRules();
                disambiguationRules.selectClosestMatch(details);
                remainingMatches.retainAll(best);
                if (remainingMatches.isEmpty()) {
                    // the intersection is empty, so we cannot choose
                    return matchs;
                }
                values.clear();
            }
        }
        if (!remainingMatches.isEmpty()) {
            // there's a subset (or not) of best matches
            return remainingMatches;
        }
        return matchs;
    }

    public List<? extends HasAttributes> getPartialMatchs() {
        List<HasAttributes> matchs = new ArrayList<HasAttributes>(1);
        for (Map.Entry<HasAttributes, MatchDetails> entry : matchDetails.entrySet()) {
            MatchDetails details = entry.getValue();
            if (!details.failure && !details.matchesByAttribute.isEmpty() && !details.hasAllAttributes) {
                matchs.add(entry.getKey());
            }
        }
        return disambiguateUsingClosestMatch(matchs);
    }

    private List<HasAttributes> selectClosestMatches(List<HasAttributes> fullMatches) {
        Set<Attribute<?>> requestedAttributes = requestedAttributesContainer.keySet();
        // if there's more than one compatible match, prefer the closest. However there's a catch.
        // We need to look at all candidates globally, and select the closest match for each attribute
        // then see if there's a non-empty intersection.
        List<HasAttributes> remainingMatches = Lists.newArrayList(fullMatches);
        final Map<HasAttributes, Object> values = Maps.newHashMap();
        for (Attribute<?> attribute : requestedAttributes) {
            Object requestedValue = requestedAttributesContainer.getAttribute(attribute);
            for (HasAttributes match : fullMatches) {
                Map<Attribute<Object>, Object> matchedAttributes = matchDetails.get(match).matchesByAttribute;
                values.put(match, matchedAttributes.get(attribute));
            }
            AttributeMatchingStrategy<Object> matchingStrategy = Cast.uncheckedCast(consumerAttributeSchema.getMatchingStrategy(attribute));
            final AttributeValue<Object> requested = AttributeValue.of(requestedValue);
            final List<HasAttributes> best = new ArrayList<HasAttributes>();
            MultipleCandidatesDetails<Object> details = new MultipleCandidatesDetails<Object>() {
                @Override
                public AttributeValue<Object> getConsumerValue() {
                    return requested;
                }

                @Override
                public Map<HasAttributes, AttributeValue<Object>> getCandidateValues() {
                    return Maps.transformValues(values, CANDIDATE_VALUES_TRANSFORM);
                }

                @Override
                public void closestMatch(HasAttributes key) {
                    best.add(key);
                }
            };
            DisambiguationRuleChainInternal<Object> disambiguationRules = (DisambiguationRuleChainInternal<Object>) matchingStrategy.getDisambiguationRules();
            disambiguationRules.selectClosestMatch(details);
            remainingMatches.retainAll(best);
            if (remainingMatches.isEmpty()) {
                // the intersection is empty, so we cannot choose
                return fullMatches;
            }
            values.clear();
        }
        if (!remainingMatches.isEmpty()) {
            // there's a subset (or not) of best matches
            return remainingMatches;
        }
        return null;
    }

    private static class MatchDetails {
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();
        private final boolean hasAllAttributes;

        private boolean failure;
        private boolean isFullMatch;

        private MatchDetails(boolean hasAllAttributes) {
            this.hasAllAttributes = hasAllAttributes;
            this.isFullMatch = hasAllAttributes;
        }

        private void update(final Attribute<Object> attribute, AttributeMatchingStrategy<Object> strategy, final Object requested, final Object provided) {
            CompatibilityCheckDetails<Object> details = new CompatibilityCheckDetails<Object>() {
                @Override
                public AttributeValue<Object> getConsumerValue() {
                    return AttributeValue.of(requested);
                }

                @Override
                public AttributeValue<Object> getProducerValue() {
                    return AttributeValue.of(provided);
                }

                @Override
                public void compatible() {
                    matchesByAttribute.put(attribute, provided);
                }

                @Override
                public void incompatible() {
                    isFullMatch = false;
                    failure = true;
                }
            };
            CompatibilityRuleChainInternal<Object> compatibilityRules = (CompatibilityRuleChainInternal<Object>) strategy.getCompatibilityRules();
            compatibilityRules.checkCompatibility(details);
        }
    }
}
