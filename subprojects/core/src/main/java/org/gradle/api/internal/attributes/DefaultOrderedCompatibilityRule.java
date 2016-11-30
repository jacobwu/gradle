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
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.OrderedCompatibilityRule;

import java.util.Comparator;

public class DefaultOrderedCompatibilityRule<T> implements OrderedCompatibilityRule<T> {
    private final Comparator<? super T> comparator;

    public DefaultOrderedCompatibilityRule(Comparator<? super T> comparator) {
        this.comparator = comparator;
    }

    @Override
    public void checkCompatibility(CompatibilityCheckDetails<T> details) {
        AttributeValue<T> consumerValue = details.getConsumerValue();
        AttributeValue<T> producerValue = details.getProducerValue();
        if (consumerValue.isPresent() && producerValue.isPresent()) {
            T consumerPresent = consumerValue.get();
            T producerPresent = producerValue.get();
            int cmp = comparator.compare(consumerPresent, producerPresent);
            if (cmp >= 0) {
                details.compatible();
            } else {
                details.incompatible();
            }
        }
    }
}
