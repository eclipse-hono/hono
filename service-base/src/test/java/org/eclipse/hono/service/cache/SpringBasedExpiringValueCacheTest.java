/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.eclipse.hono.cache.ExpiringValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    @BeforeEach
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
        assertThat(result).isEqualTo("hello");
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
        assertThat(result).isNull();
        // and the expired value has been evicted from the cache
        verify(springCache).evict("key");
    }

    /**
     * Verifies that a cache entry is not expired if the corresponding put method without an expiry is called.
     */
    @Test
    public void testPutWithoutExpiryDoesNotChangeExpiry() {
        final Instant expectedExpiry = Instant.now().plusSeconds(60);
        final String expectedValue = "4712";

        // GIVEN a cache that contains a non-expired value
        final ExpiringValue<String> value = mock(ExpiringValue.class);
        when(value.isExpired()).thenReturn(Boolean.FALSE);
        when(value.getValue()).thenReturn("4711");
        when(value.getExpirationTime()).thenReturn(expectedExpiry);
        when(springCache.get("key", ExpiringValue.class)).thenReturn(value);

        // WHEN trying to put a value for the key without a new expiry
        final boolean result = cache.update("key", expectedValue);

        // THEN the new cache value has the expiration time of the value it replaced
        final ArgumentCaptor<ExpiringValue<String>> captor = ArgumentCaptor.forClass(ExpiringValue.class);
        verify(springCache).put(eq("key"), captor.capture());

        final ExpiringValue<String> actualValue = captor.getValue();
        assertThat(actualValue.getValue()).isEqualTo(expectedValue);
        assertThat(actualValue.getExpirationTime()).isEqualTo(expectedExpiry);

        assertThat(result).isTrue();
        verify(springCache, never()).evict("key");
    }

    /**
     * Verifies that an evicted entry is not overwritten if a new value without an expiry being set is put into the
     * cache.
     */
    @Test
    public void testPutWithoutExpiryDoesNotReplaceAnEvictedEntry() {
        // GIVEN a cache that contains an expired value
        final ExpiringValue<String> value = mock(ExpiringValue.class);
        when(value.isExpired()).thenReturn(Boolean.TRUE);
        when(springCache.get("key", ExpiringValue.class)).thenReturn(value);

        // WHEN trying to put a value for the key without a new expiry
        final boolean result = cache.update("key", "4712");

        // THEN the cache should not be updated
        assertThat(result).isFalse();
        verify(springCache, never()).put(eq("key"), any());
    }

}
