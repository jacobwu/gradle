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

import org.gradle.api.attributes.AttributeValue;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.OrderedDisambiguationRule;

import java.util.Collection;
import java.util.Map;

public class DefaultOrderedDisambiguationRule<T extends Comparable<T>> implements OrderedDisambiguationRule<T> {
    private boolean pickFirst;

    @Override
    public OrderedDisambiguationRule<T> pickFirst() {
        pickFirst = true;
        return this;
    }

    @Override
    public OrderedDisambiguationRule<T> pickLast() {
        pickFirst = false;
        return this;
    }

    @Override
    public void selectClosestMatch(MultipleCandidatesDetails<T> details) {
        Collection<AttributeValue<T>> values = details.getCandidateValues().values();
        T min = null;
        T max = null;
        for (AttributeValue<T> value : values) {
            if (value.isPresent()) {
                T v = value.get();
                if (min == null || v.compareTo(min) < 0) {
                    min = v;
                }
                if (max == null || v.compareTo(max) > 0) {
                    max = v;
                }
            }
        }
        T cmp = pickFirst ? min : max;
        if (cmp != null) {
            for (Map.Entry<HasAttributes, AttributeValue<T>> entry : details.getCandidateValues().entrySet()) {
                HasAttributes key = entry.getKey();
                AttributeValue<T> value = entry.getValue();
                if (value.isPresent() && value.get().equals(cmp)) {
                    details.closestMatch(key);
                }
            }
        }
    }
}
