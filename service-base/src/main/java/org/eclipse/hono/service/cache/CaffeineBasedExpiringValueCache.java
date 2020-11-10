/**
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

import com.github.benmanes.caffeine.cache.Cache;


/**
 * A cache for expiring values based on Caffeine.
 *
 * @param <V> The type of values that the cache supports.
 */
public class CaffeineBasedExpiringValueCache<V> implements ExpiringValueCache<Object, V> {

    private static final Logger LOG = LoggerFactory.getLogger(CaffeineBasedExpiringValueCache.class);

    private final Cache<Object, Object> cache;

    /**
     * Creates a new cache.
     *
     * @param cache The cache instance to use for storing values.
     * @throws NullPointerException if cache is {@code null}.
     */
    public CaffeineBasedExpiringValueCache(final Cache<Object, Object> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public void put(final Object key, final V value, final Instant expirationTime) {

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
    public void put(final Object key, final V value, final Duration maxAge) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(maxAge);

        put(key, value, Instant.now().plus(maxAge));
    }

    @Override
    public V get(final Object key) {

        if (key == null) {
            return null;
        } else {
            final Object value = cache.getIfPresent(key);
            if (value == null) {
                LOG.trace("cache miss [key: {}]", key);
                return null;
            } else {
                @SuppressWarnings("unchecked")
                final ExpiringValue<V> v = (ExpiringValue<V>) value;
                if (v.isExpired()) {
                    LOG.trace("cache hit expired [key: {}]", key);
                    cache.invalidate(key);
                    return null;
                } else {
                    LOG.trace("cache hit [key: {}]", key);
                    return v.getValue();
                }
            }
        }
    }
}
