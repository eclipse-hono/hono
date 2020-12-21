/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class CacheDirectiveTest {

    @Test
    void shouldCompareCacheDirectivesWhenDifferentMaxAge() {
        final CacheDirective cacheDirective1 = CacheDirective.maxAgeDirective(1L);
        final CacheDirective cacheDirective2 = CacheDirective.maxAgeDirective(2L);
        assertTrue(cacheDirective1.compareTo(cacheDirective2) == -1);
        assertTrue(cacheDirective2.compareTo(cacheDirective1) == 1);
    }

    @Test
    void shouldCompareCacheDirectivesWhenSameMaxAge() {
        final CacheDirective cacheDirective1 = CacheDirective.maxAgeDirective(1L);
        final CacheDirective cacheDirective2 = CacheDirective.maxAgeDirective(1L);
        assertTrue(cacheDirective1.compareTo(cacheDirective2) == 0);
        assertTrue(cacheDirective2.compareTo(cacheDirective1) == 0);
    }

    @Test
    void shouldCompareCacheDirectivesWhenOtherIsNoCache() {
        final CacheDirective cacheDirective1 = CacheDirective.maxAgeDirective(1L);
        final CacheDirective cacheDirective2 = CacheDirective.noCacheDirective();
        assertTrue(cacheDirective1.compareTo(cacheDirective2) == -1);
        assertTrue(cacheDirective2.compareTo(cacheDirective1) == 1);
    }

    @Test
    void shouldCompareCacheDirectivesWhenBothAreNoCache() {
        final CacheDirective cacheDirective1 = CacheDirective.noCacheDirective();
        final CacheDirective cacheDirective2 = CacheDirective.noCacheDirective();
        assertTrue(cacheDirective1.compareTo(cacheDirective2) == 0);
        assertTrue(cacheDirective2.compareTo(cacheDirective1) == 0);
    }

    @Test
    void shouldSortListOfCacheDirectives() {
        final CacheDirective cacheDirective1 = CacheDirective.maxAgeDirective(1L);
        final CacheDirective cacheDirective2 = CacheDirective.maxAgeDirective(2L);
        final CacheDirective cacheDirective3 = CacheDirective.maxAgeDirective(3L);
        final CacheDirective cacheDirective4 = CacheDirective.noCacheDirective();
        final CacheDirective[] cacheDirectives = new CacheDirective[]{cacheDirective3, cacheDirective1, cacheDirective4, cacheDirective2};
        Arrays.sort(cacheDirectives);
        assertArrayEquals(new CacheDirective[]{cacheDirective1, cacheDirective2, cacheDirective3, cacheDirective4},
                cacheDirectives);
    }
}
