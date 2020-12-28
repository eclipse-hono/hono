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

package org.eclipse.hono.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.cache.ExpiringValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;


/**
 * Tests verifying behavior of {@link CaffeineBasedExpiringValueCache}.
 *
 */
public class CaffeineBasedExpiringValueCacheTest {

    private Cache<Object, Object> caffeineCache;
    private CaffeineBasedExpiringValueCache<String> cache;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        caffeineCache = mock(Cache.class);
        cache = new CaffeineBasedExpiringValueCache<>(caffeineCache);
    }

    /**
     * Verifies that the cache returns non-expired values.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetResponseFromCache() {

        // GIVEN a cache that contains a non-expired response
        final ExpiringValue<String> value = mock(ExpiringValue.class);
        when(value.isExpired()).thenReturn(Boolean.FALSE);
        when(value.getValue()).thenReturn("hello");
        when(caffeineCache.getIfPresent("key")).thenReturn(value);

        // WHEN trying to get a cached response for the key
        final String result = cache.get("key");

        // THEN the result is not null
        assertThat(result).isEqualTo("hello");
        // and the value has not been evicted from the cache
        verify(caffeineCache, never()).invalidate("key");
    }

    /**
     * Verifies that the cache evicts expired values.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetResponseFromCacheEvictsExpiredValue() {

        // GIVEN a cache that contains an expired value
        final ExpiringValue<String> value = mock(ExpiringValue.class);
        when(value.isExpired()).thenReturn(Boolean.TRUE);
        when(caffeineCache.getIfPresent("key")).thenReturn(value);

        // WHEN trying to get a cached response for the key
        final String result = cache.get("key");

        // THEN the result is null
        assertThat(result).isNull();
        // and the expired value has been evicted from the cache
        verify(caffeineCache).invalidate("key");
    }

}
