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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * A cache manager based on Caffeine.
 */
public class CaffeineCacheProvider implements CacheProvider {

    private final Caffeine<Object, Object> cacheSpec;
    private final Map<String, ExpiringValueCache<Object, Object>> caches = new ConcurrentHashMap<>();

    /**
     * Creates a new instance based on the provided {@link com.github.benmanes.caffeine.cache.CaffeineSpec}.
     *
     * @param cacheSpec The cache configuration to use for creating cache instances.
     * @throws NullPointerException if cache spec is {@code null}.
     */
    public CaffeineCacheProvider(final Caffeine<Object, Object> cacheSpec) {
        this.cacheSpec = Objects.requireNonNull(cacheSpec);
    }

    /**
     * {@inheritDoc}
     *
     * @return The existing cache for the given name or a newly created one, if no
     *         cache for the given name exists yet.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> ExpiringValueCache<K, V> getCache(final String cacheName) {
        Objects.requireNonNull(cacheName);
        return (ExpiringValueCache<K, V>) caches.computeIfAbsent(cacheName, name -> {
            return new CaffeineBasedExpiringValueCache<>(cacheSpec.build());
        });
    }
}
