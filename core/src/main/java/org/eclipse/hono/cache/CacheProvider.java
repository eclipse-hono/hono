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

package org.eclipse.hono.cache;

/**
 * A provider for cache instances.
 */
public interface CacheProvider {

    /**
     * Gets a new instance of a cache by name.
     * <p>
     * It is up to the provider how the cache instance is created, or it creates a new or returns an existing one.
     * 
     * @param cacheName the name of the cache to get. Must not be {@code null}.
     * @param <K> The type of keys that the cache supports.
     * @param <V> The type of values that the cache supports.
     * 
     * @return The new cache instance, may be {@code null} if no cache with that name can be provided.
     * 
     * @throws NullPointerException if the cache name is {@code null}.
     */
    <K, V> ExpiringValueCache<K, V> getCache(String cacheName);
}
