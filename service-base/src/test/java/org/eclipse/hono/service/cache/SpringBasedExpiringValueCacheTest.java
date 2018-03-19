/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.cache;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

import org.eclipse.hono.cache.ExpiringValue;
import org.eclipse.hono.service.cache.SpringBasedExpiringValueCache;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.Cache;


/**
 * Tests verifying behavior of {@link SpringBasedExpiringValueCache}.
 *
 */
public class SpringBasedExpiringValueCacheTest {

    private Cache springCache;
    private SpringBasedExpiringValueCache<String, String> cache;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        springCache = mock(Cache.class);
        cache = new SpringBasedExpiringValueCache<>(springCache);
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
        when(springCache.get("key", ExpiringValue.class)).thenReturn(value);

        // WHEN trying to get a cached response for the key
        final String result = cache.get("key");

        // THEN the result is not null
        assertThat(result, is("hello"));
        // and the value has not been evicted from the cache
        verify(springCache, never()).evict("key");
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
        when(springCache.get("key", ExpiringValue.class)).thenReturn(value);

        // WHEN trying to get a cached response for the key
        final String result = cache.get("key");

        // THEN the result is null
        assertNull(result);
        // and the expired value has been evicted from the cache
        verify(springCache).evict("key");
    }

}
