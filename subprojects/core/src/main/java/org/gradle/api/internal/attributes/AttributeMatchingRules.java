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

import org.gradle.api.attributes.CompatibilityRule;
import org.gradle.api.attributes.OrderedCompatibilityRule;
import org.gradle.api.attributes.OrderedDisambiguationRule;
import org.gradle.internal.Cast;

public abstract class AttributeMatchingRules {
    private static final EqualityCompatibilityRule EQUALITY_RULE = new EqualityCompatibilityRule();

    public static <T> CompatibilityRule<T> equalityCompatibility() {
        return Cast.uncheckedCast(EQUALITY_RULE);
    }

    public static <T extends Comparable<T>> OrderedCompatibilityRule<T> orderedCompatibility() {
        return new DefaultOrderedCompatibilityRule<T>();
    }

    public static <T extends Comparable<T>> OrderedDisambiguationRule<T> orderedDisambiguation() {
        return new DefaultOrderedDisambiguationRule<T>();
    }
}
