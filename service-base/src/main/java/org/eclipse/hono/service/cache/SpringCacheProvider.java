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

import static java.util.Objects.requireNonNull;

import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * A cache manager based on Spring Cache.
 */
public class SpringCacheProvider implements CacheProvider {

    private final CacheManager manager;

    /**
     * Create a new instance based on the provided {@link CacheManager} instance.
     * 
     * @param manager the cache manager to use, must not be {@code null}
     */
    public SpringCacheProvider(final CacheManager manager) {
        requireNonNull(manager);
        this.manager = manager;
    }

    @Override
    public <K, V> ExpiringValueCache<K, V> getCache(final String cacheName) {
        requireNonNull(cacheName);
        final Cache cache = this.manager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        return new SpringBasedExpiringValueCache<>(cache);
    }

}
