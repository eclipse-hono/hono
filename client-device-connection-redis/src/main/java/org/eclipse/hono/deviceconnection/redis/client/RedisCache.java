/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceconnection.redis.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;

/**
 * A vert.x based Redis implementation of Hono's {@link Cache} interface.
 * <p>
 * All operations are implemented as single Redis commands, using server-side Lua
 * scripts where atomicity across multiple commands is required. No operation
 * establishes connection-level state (such as {@code WATCH} or {@code MULTI}):
 * the underlying vert.x Redis client recycles pooled connections without
 * resetting them, so any leaked per-connection state would corrupt unrelated
 * subsequent operations borrowing the same connection.
 * <p>
 * Executing Lua scripts requires the {@code EVAL} command (Redis 2.6 or later)
 * to be permitted for the configured user ({@code @scripting} ACL category);
 * {@link #start()} verifies this and fails otherwise.
 * <p>
 * If the Redis server runs in cluster mode, which the cache determines by means of the
 * {@code INFO cluster} command, operations involving multiple keys are split by hash slot.
 * Storing multiple values with a lifespan is then only atomic per hash slot.
 */
public class RedisCache implements Cache<String, String>, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCache.class);

    /**
     * Atomically deletes a key only if it currently holds the given value.
     * Returns the number of deleted keys (1 or 0).
     */
    private static final String COMPARE_AND_DELETE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end""";

    /**
     * Atomically sets each key to its corresponding value with a shared
     * time-to-live. ARGV[1] holds the TTL in milliseconds, ARGV[i + 1] the
     * value for KEYS[i].
     */
    private static final String PUT_ALL_WITH_LIFESPAN_SCRIPT = """
            for i = 1, #KEYS do
                redis.call('set', KEYS[i], ARGV[i + 1], 'PX', ARGV[1])
            end
            return #KEYS""";

    private final RedisAPI api;
    private volatile Boolean clusterMode;

    private RedisCache(final RedisAPI api) {
        this.api = Objects.requireNonNull(api);
    }

    /**
     * Creates a cache instance backed by the given Redis API.
     *
     * @param api The API instance to use for accessing Redis.
     * @return The cache.
     * @throws NullPointerException if api is {@code null}.
     */
    public static RedisCache from(final RedisAPI api) {
        return new RedisCache(api);
    }

    @Override
    public Future<Void> start() {
        return checkForCacheAvailability()
                .compose(ok -> api.eval(List.of("return 1", "0"))
                        .recover(t -> Future.failedFuture(new IllegalStateException(
                                "the Redis server does not permit Lua scripting (EVAL) for the configured user; "
                                        + "this cache requires the @scripting command category to be allowed",
                                t))))
                .<Void>mapEmpty()
                .onFailure(t -> LOG.error("Redis cache failed to start", t));
    }

    @Override
    public Future<Void> stop() {
        return Future.succeededFuture();
    }

    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        return api.ping(List.of())
                .map(new JsonObject());
    }

    @Override
    public Future<Void> put(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return api.set(List.of(key, value))
                .mapEmpty();
    }

    @Override
    public Future<Void> put(final String key, final String value, final long lifespan, final TimeUnit lifespanUnit) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(lifespanUnit);

        final List<String> params = new ArrayList<>(List.of(key, value));
        final long millis = lifespanUnit.toMillis(lifespan);
        if (millis > 0) {
            params.addAll(List.of("PX", String.valueOf(millis)));
        }
        return api.set(params)
                .mapEmpty();
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data) {
        Objects.requireNonNull(data);

        if (data.isEmpty()) {
            return Future.succeededFuture();
        }
        final List<String> keyValues = new ArrayList<>(data.size() * 2);
        data.forEach((k, v) -> {
            keyValues.add(k);
            keyValues.add(v);
        });
        return api.mset(keyValues)
                .mapEmpty();
    }

    @Override
    public Future<Void> putAll(final Map<? extends String, ? extends String> data, final long lifespan,
            final TimeUnit lifespanUnit) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(lifespanUnit);

        final long millis = lifespanUnit.toMillis(lifespan);
        if (millis <= 0 || data.isEmpty()) {
            return putAll(data);
        }
        return isClusterMode()
                .compose(cluster -> {
                    if (!cluster) {
                        return putAllWithLifespanInSingleScript(data, millis);
                    }
                    // a script may only access keys of a single hash slot in a Redis Cluster
                    final Map<Integer, Map<String, String>> dataBySlot = data.entrySet().stream()
                            .collect(Collectors.groupingBy(
                                    entry -> RedisHashSlot.of(entry.getKey()),
                                    Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    return Future.all(dataBySlot.values().stream()
                            .map(slotData -> putAllWithLifespanInSingleScript(slotData, millis))
                            .toList())
                            .mapEmpty();
                });
    }

    private Future<Void> putAllWithLifespanInSingleScript(
            final Map<? extends String, ? extends String> data,
            final long lifespanMillis) {
        final List<String> args = new ArrayList<>(data.size() * 2 + 3);
        args.add(PUT_ALL_WITH_LIFESPAN_SCRIPT);
        args.add(String.valueOf(data.size()));
        final List<String> values = new ArrayList<>(data.size());
        data.forEach((k, v) -> {
            args.add(k);
            values.add(v);
        });
        args.add(String.valueOf(lifespanMillis));
        args.addAll(values);
        return api.eval(args)
                .mapEmpty();
    }

    @Override
    public Future<String> get(final String key) {
        Objects.requireNonNull(key);

        return api.get(key)
                .map(value -> value == null ? null : value.toString(StandardCharsets.UTF_8));
    }

    @Override
    public Future<Boolean> remove(final String key, final String value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return api.eval(List.of(COMPARE_AND_DELETE_SCRIPT, "1", key, value))
                .map(response -> response.toInteger() == 1);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the Redis server runs in cluster mode, the values are retrieved by means of one {@code MGET}
     * command per hash slot. A Redis Cluster client would otherwise split the command by hash slot and
     * concatenate the replies in arbitrary order, so that the values could not be mapped to their keys.
     */
    @Override
    public Future<Map<String, String>> getAll(final Set<? extends String> keys) {
        Objects.requireNonNull(keys);

        if (keys.isEmpty()) {
            return Future.succeededFuture(new HashMap<>());
        }
        final List<String> keyList = keys.stream().map(String::valueOf).toList();
        return isClusterMode()
                .compose(cluster -> {
                    if (!cluster) {
                        return getAllInSingleCommand(keyList);
                    }
                    final List<Future<Map<String, String>>> results = groupByHashSlot(keyList).stream()
                            .map(this::getAllInSingleCommand)
                            .toList();
                    return Future.all(results)
                            .map(all -> {
                                final Map<String, String> result = new HashMap<>(keyList.size());
                                results.forEach(slotResult -> result.putAll(slotResult.result()));
                                return result;
                            });
                });
    }

    private Future<Map<String, String>> getAllInSingleCommand(final List<String> keyList) {
        return api.mget(keyList)
                .map(values -> {
                    final Map<String, String> result = new HashMap<>(keyList.size());
                    final Iterator<String> keyIterator = keyList.iterator();
                    values.forEach(value -> {
                        final String key = keyIterator.next();
                        // a null entry means the key does not exist; omit it from the
                        // result, matching the behavior of the Infinispan based caches
                        if (value != null) {
                            result.put(key, value.toString(StandardCharsets.UTF_8));
                        }
                    });
                    return result;
                });
    }

    private static Collection<List<String>> groupByHashSlot(final List<String> keys) {
        return keys.stream()
                .collect(Collectors.groupingBy(RedisHashSlot::of))
                .values();
    }

    /**
     * Checks if the Redis server runs in cluster mode.
     * <p>
     * The outcome of a successful check is remembered. If the check fails, cluster mode is assumed,
     * which is correct in any case, and the check is repeated for the next operation.
     *
     * @return A succeeded future indicating whether the server runs in cluster mode.
     */
    private Future<Boolean> isClusterMode() {
        final Boolean knownClusterMode = clusterMode;
        if (knownClusterMode != null) {
            return Future.succeededFuture(knownClusterMode);
        }
        return api.info(List.of("cluster"))
                .map(info -> {
                    final boolean cluster = info != null
                            && info.toString(StandardCharsets.UTF_8).contains("cluster_enabled:1");
                    if (clusterMode == null) {
                        LOG.info("Redis server {} in cluster mode", cluster ? "runs" : "does not run");
                    }
                    clusterMode = cluster;
                    return cluster;
                })
                .otherwise(t -> {
                    LOG.debug("failed to determine if Redis server runs in cluster mode, assuming it does", t);
                    return Boolean.TRUE;
                });
    }
}
