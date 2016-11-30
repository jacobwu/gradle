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

package org.gradle.api.internal.attributes;

import com.google.common.collect.Lists;
import org.gradle.api.attributes.AttributeValue;
import org.gradle.api.attributes.DisambiguationRule;
import org.gradle.api.attributes.DisambiguationRuleChain;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;

import java.util.List;
import java.util.Map;

public class DefaultDisambiguationRuleChain<T> implements DisambiguationRuleChain<T> {

    private final List<DisambiguationRule<T>> rules = Lists.newArrayList();

    private boolean selectAllEventually = true;

    @Override
    public void add(DisambiguationRule<T> rule) {
        this.rules.add(rule);
    }

    @Override
    public void setRules(List<DisambiguationRule<T>> disambiguationRules) {
        this.rules.clear();
        this.rules.addAll(disambiguationRules);
    }

    @Override
    public void eventuallySelectAll() {
        selectAllEventually = true;
    }

    @Override
    public void eventuallySelectNone() {
        selectAllEventually = false;
    }

    @Override
    public void selectClosestMatch(MultipleCandidatesDetails<T> details) {
        State<T> state = new State<T>(details);
        for (DisambiguationRule<T> rule : rules) {
            rule.selectClosestMatch(state);
            if (state.determined) {
                return;
            }
        }
        if (!state.determined && selectAllEventually) {
            SelectAllCompatibleRule.apply(details);
        }
    }

    private static class State<T> implements MultipleCandidatesDetails<T> {
        private final MultipleCandidatesDetails<T> delegate;
        private boolean determined;

        private State(MultipleCandidatesDetails<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public AttributeValue<T> getConsumerValue() {
            return delegate.getConsumerValue();
        }

        @Override
        public Map<HasAttributes, AttributeValue<T>> getCandidateValues() {
            return delegate.getCandidateValues();
        }

        @Override
        public void closestMatch(HasAttributes key) {
            determined = true;
            delegate.closestMatch(key);
        }

    }
}
