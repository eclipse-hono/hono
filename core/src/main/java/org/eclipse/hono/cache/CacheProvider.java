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

package org.eclipse.hono.cache;

/**
 * A provider for cache instances.
 */
public interface CacheProvider {

    /**
     * Gets a cache instance by name.
     * <p>
     * It is up to the implementation how the cache instance is created
     * and/or if a new instance is being created or an existing one is returned.
     *
     * @param cacheName The name of the cache to get.
     * @param <K> The type of keys that the cache supports.
     * @param <V> The type of values that the cache supports.
     * @return The new cache instance, may be {@code null} if no cache with that name can be provided.
     * @throws NullPointerException if the cache name is {@code null}.
     */
    <K, V> ExpiringValueCache<K, V> getCache(String cacheName);
}
