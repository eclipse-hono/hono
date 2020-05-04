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

package org.eclipse.hono.deviceconnection.infinispan.client;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A simple {@code Map} like interface to a data grid cache.
 *
 * @param <K> The type of keys used for looking up data.
 * @param <V> The type of values stored in grid.
 */
public interface Cache<K, V> {

    /**
     * Checks if the cache is connected to the data grid.
     *
     * @return A future that is completed with information about a successful check's result.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServerErrorException}.
     */
    Future<JsonObject> checkForCacheAvailability();

    /**
     * Puts a value to the cache.
     *
     * @param key The key.
     * @param value The value.
     * @return A succeeded future containing the previous value or {@code null} if the
     *         cache didn't contain the key yet.
     *         A failed future if the value could not be stored in the cache.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<V> put(K key, V value);

    /**
     * Puts a value to the cache.
     *
     * @param key The key.
     * @param value The value.
     * @param lifespan The lifespan of the entry. A negative value is interpreted as an unlimited lifespan.
     * @param lifespanUnit The time unit for the lifespan.
     * @return A succeeded future containing the previous value or {@code null} if the
     *         cache didn't contain the key yet.
     *         A failed future if the value could not be stored in the cache.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<V> put(K key, V value, long lifespan, TimeUnit lifespanUnit);

    /**
     * Replaces the entry for a key only if currently mapped to a given value.
     *
     * @param key The key.
     * @param oldValue The value to overwrite.
     * @param newValue The value to store.
     * @param lifespan The lifespan of the entry. A negative value is interpreted as an unlimited lifespan.
     * @param lifespanUnit The time unit for the lifespan.
     * @return A succeeded future containing a boolean, indicating whether the value was replaced or not.
     *         A failed future if the value could not be stored in the cache.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Boolean> replace(K key, V oldValue, V newValue, long lifespan, TimeUnit lifespanUnit);

    /**
     * Gets a value from the cache.
     *
     * @param key The key.
     * @return A succeeded future containing the value or {@code null} if the
     *         cache didn't contain the key yet.
     *         A failed future if the value could not be read from the cache.
     * @throws NullPointerException if key is {@code null}.
     */
    Future<V> get(K key);

    /**
     * Remove a key/value mapping from the cache.
     *
     * @param key The key.
     * @param value The value.
     * @return A succeeded future containing {@code true} if the key was
     *         mapped to the value, {@code false} otherwise.
     *         A failed future if the value could not be remove from the cache.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Boolean> remove(K key, V value);

    /**
     * Gets the values for the specified keys from the cache.
     *
     * @param keys The keys.
     * @return A succeeded future containing a map with key/value pairs.
     * @throws NullPointerException if keys is {@code null}.
     */
    Future<Map<K, V>> getAll(Set<? extends K> keys);
}
