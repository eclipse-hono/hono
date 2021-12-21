/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.client.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link AnnotatedCacheKey}.
 *
 */
public class AnnotatedCacheKeyTest {

    public static final String RAW_KEY_UNDER_TEST = "test";
    private AnnotatedCacheKey<String> underTest;

    @BeforeEach
    void setUp() {
        underTest = new AnnotatedCacheKey<>(RAW_KEY_UNDER_TEST);
    }

    /**
     * Verifies that the key added in the constructor is returned by {@link AnnotatedCacheKey#getKey()}.
     */
    @Test
    public void testThatKeyIsReturned() {
        assertThat(underTest.getKey()).isEqualTo(RAW_KEY_UNDER_TEST);

        final AnnotatedCacheKey<Long> longAnnotatedCacheKey = new AnnotatedCacheKey<>(99L);
        assertThat(longAnnotatedCacheKey.getKey()).isEqualTo(99L);
    }

    /**
     * Verifies that the attributes can be added and retrieved.
     */
    @Test
    public void testThatAttributeIsAdded() {
        final String attributeKey = "the-key";
        final String attributeValue = "the-value";

        underTest.putAttribute(attributeKey, attributeValue);

        assertThat(
                underTest.getAttribute(attributeKey)
                        .orElseThrow(() -> new RuntimeException("the expected attribute is missing")))
                                .isEqualTo(attributeValue);
    }

    /**
     * Verifies that {@link AnnotatedCacheKey#equals(Object)} only compares the key and not the attributes.
     */
    @Test
    public void testThatEqualsDoesNotTakeAttributesIntoAccount() {
        underTest.putAttribute("attribute1", "value1");

        final AnnotatedCacheKey<String> second = new AnnotatedCacheKey<>(RAW_KEY_UNDER_TEST);
        second.putAttribute("attribute1", "other-value");
        second.putAttribute("attribute2", "value2");

        assertThat(second).isEqualTo(underTest);
    }

    /**
     * Verifies that {@link AnnotatedCacheKey#hashCode()} only compares the key and not the attributes.
     */
    @Test
    public void testThatHashCodeDoesNotTakeAttributesIntoAccount() {
        underTest.putAttribute("attribute1", "value1");

        final AnnotatedCacheKey<String> second = new AnnotatedCacheKey<>(RAW_KEY_UNDER_TEST);
        second.putAttribute("attribute1", "other-value");
        second.putAttribute("attribute2", "value2");

        assertThat(second.hashCode()).isEqualTo(underTest.hashCode());
    }
}
