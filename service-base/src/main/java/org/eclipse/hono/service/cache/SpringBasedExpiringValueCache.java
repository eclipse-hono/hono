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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.cache.BasicExpiringValue;
import org.eclipse.hono.cache.ExpiringValue;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;


/**
 * A cache for expiring values based on Spring's Cache abstraction.
 * 
 * @param <K> The type of keys that the cache supports.
 * @param <V> The type of values that the cache supports.
 */
public class SpringBasedExpiringValueCache<K, V> implements ExpiringValueCache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(SpringBasedExpiringValueCache.class);

    private final Cache cache;

    /**
     * Creates a new cache.
     * 
     * @param cache The Spring cache instance to use for storing values.
     */
    public SpringBasedExpiringValueCache(final Cache cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public void put(final K key, final V value, final Instant expirationTime) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(expirationTime);

        if (Instant.now().isBefore(expirationTime)) {
            final ExpiringValue<V> expiringValue = new BasicExpiringValue<>(value, expirationTime);
            cache.put(key, expiringValue);
        } else {
            throw new IllegalArgumentException("value is already expired");
        }
    }

    @Override
    public void put(final K key, final V value, final Duration maxAge) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(maxAge);

        put(key, value, Instant.now().plus(maxAge));
    }

    @Override
    public V get(final K key) {

        if (key == null) {
            return null;
        } else {
            @SuppressWarnings("unchecked")
            ExpiringValue<V> value = cache.get(key, ExpiringValue.class);
            if (value == null) {
                LOG.trace("cache miss [key: {}]", key);
                return null;
            } else if (value.isExpired()) {
                LOG.trace("cache hit expired [key: {}]", key);
                cache.evict(key);
                return null;
            } else {
                LOG.trace("cache hit [key: {}]", key);
                return value.getValue();
            }
        }
    }

}
