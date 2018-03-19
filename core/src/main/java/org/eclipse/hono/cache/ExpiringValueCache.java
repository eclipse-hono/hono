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

import java.time.Duration;
import java.time.Instant;

/**
 * A cache for values that have a limited validity period.
 * 
 * @param <K> The type of keys that the cache supports.
 * @param <V> The type of values that the cache supports.
 */
public interface ExpiringValueCache<K, V> {

    /**
     * Puts a value to the cache.
     * <p>
     * Any previous value for the key will be replaced with the new one.
     * 
     * @param key The key under which the value is stored.
     * @param value The value to store.
     * @param expirationTime The point in time after which the value should be
     *                       considered invalid.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the expiration time is not in the future.
     */
    void put(K key, V value, Instant expirationTime);

    /**
     * Puts a value to the cache.
     * <p>
     * Any previous value for the key will be replaced with the new one.
     * 
     * @param key The key under which the value is stored.
     * @param value The value to store.
     * @param maxAge The duration (starting from now) after which the
     *               value should be considered invalid.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if the expiration time is not in the future.
     */
    void put(K key, V value, Duration maxAge);

    /**
     * Gets a value from the cache.
     * 
     * @param key The key to get the value for.
     * @return The value or {@code null} if no value exists for the key or
     *         if the value is expired.
     */
    V get(K key);
}
