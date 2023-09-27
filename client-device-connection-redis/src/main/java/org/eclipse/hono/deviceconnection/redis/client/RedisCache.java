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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.deviceconnection.common.Cache;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Response;

/**
 * TODO.
 * @param <K> TODO
 * @param <V> TODO
 */
public class RedisCache<K, V> implements Cache<K, V>, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCache.class);

    private static final int MAX_RECONNECT_RETRIES = 16;

    private final Vertx vertx;
    private final RedisRemoteConfigurationProperties properties;

    private Redis redis;
    private RedisConnection client;
    private final AtomicBoolean CONNECTING = new AtomicBoolean();

    /**
     * TODO.
     *
     * @param vertx TODO.
     * @param properties TODO.
     */
    private RedisCache(final Vertx vertx, final RedisRemoteConfigurationProperties properties) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(properties);

        this.vertx = vertx;
        this.properties = properties;

        LOG.info("Initializing REDIS cache!");
        //createRedisClient()
        //        .onSuccess( c -> LOG.info("Connected to Redis"))
        //        .onFailure( t -> LOG.error("Could not connect to Redis", t));
    }

    /**
     * TODO.
     *
     * @param vertx TODO.
     * @param properties TODO.
     *
     * @return TODO.
     */
    public static RedisCache<String, String> from(
            final Vertx vertx, final RedisRemoteConfigurationProperties properties) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(properties);

        return new RedisCache<>(vertx, properties);
    }

    @Override
    public Future<Void> start() {
        LOG.info("REDIS: starting cache");
        return createRedisClient()
                .flatMap(c -> Future.succeededFuture());
        /*
        final Promise<Void> promise = Promise.promise();
        createRedisClient()
                .onSuccess(c -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
         */
        /*
        redis = Redis.createClient(vertx, properties);
        redis.connect()
                .onSuccess(c -> client = c)
                .onFailure(promise::fail);
        return promise.future();
         */
    }

    @Override
    public Future<Void> stop() {
        LOG.info("REDIS: stopping cache");
        redis.close();
        return Future.succeededFuture();
    }

    /*
     * Will create a redis client and set up a reconnect handler when there is an exception in the connection.
     */
    private Future<RedisConnection> createRedisClient() {
        final Promise<RedisConnection> promise = Promise.promise();

        // make sure to invalidate old connection if present
        if (redis != null) {
            redis.close();;
        }

        if (CONNECTING.compareAndSet(false, true)) {
            redis = Redis.createClient(vertx, properties);
            redis.connect()
                    .onSuccess(conn -> {
                        client = conn;

                        // make sure the client is reconnected on error
                        // eg, the underlying TCP connection is closed but the client side doesn't know it yet
                        //     the client tries to use the staled connection to talk to server. An exceptions will be raised
                        conn.exceptionHandler(e -> attemptReconnect(0));

                        // make sure the client is reconnected on connection close
                        // eg, the underlying TCP connection is closed with normal 4-Way-Handshake
                        //     this handler will be notified instantly
                        conn.endHandler(placeHolder -> attemptReconnect(0));

                        // allow further processing
                        promise.complete(conn);
                        CONNECTING.set(false);
                    }).onFailure(t -> {
                        promise.fail(t);
                        CONNECTING.set(false);
                    });
        } else {
            promise.complete();
        }

        return promise.future();
    }

    private void attemptReconnect(final int retry) {
        if (retry > MAX_RECONNECT_RETRIES) {
            // we should stop now, as there's nothing we can do.
            CONNECTING.set(false);
        } else {
            // retry with backoff up to 10240 ms
            final long backoff = (long) (Math.pow(2, Math.min(retry, 10)) * 10);

            vertx.setTimer(backoff, timer -> createRedisClient().onFailure(t -> attemptReconnect(retry + 1)));
        }
    }

    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        LOG.info("REDIS: checking for cache availability");

        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            return api.ping(List.of())
                    .eventually(ignored -> connection.close())
                    .mapEmpty();
        });
    }

    @Override
    public Future<Void> put(final K key, final V value) {
        LOG.info("REDIS: put {}={}", key, value);
        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            return api.set(List.of(String.valueOf(key), String.valueOf(value)))
                    .eventually(ignored -> connection.close())
                    .mapEmpty();
        });
    }

    @Override
    public Future<Void> put(final K key, final V value, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("REDIS: put {}={} ({} {})", key, value, lifespan, lifespanUnit);
        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            final List<String> params = new ArrayList<>(List.of(key.toString(), value.toString()));
            final long millis = lifespanUnit.toMillis(lifespan);
            if (millis > 0) {
                params.addAll(List.of("PX", String.valueOf(millis)));
            }
            return api.set(params)
                    .eventually(ignored -> connection.close())
                    .mapEmpty();
        });
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data) {
        LOG.info("REDIS: putAll ({})", data.size());
        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            final List<String> keyValues = new ArrayList<>(data.size() * 2);
            data.forEach((k, v) -> {
                keyValues.add(k.toString());
                keyValues.add(v.toString());
            });
            return api.mset(keyValues)
                    .eventually(ignored -> connection.close())
                    .mapEmpty();
        });
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("REDIS: putAll ({}) ({} {})", data.size(), lifespan, lifespanUnit);
        Objects.requireNonNull(client);

        final long millis = lifespanUnit.toMillis(lifespan);
        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            return api.multi()
                    .flatMap(ignored -> {
                        final List<Future<Response>> futures = new ArrayList<>(data.size());
                        data.forEach((k, v) -> {
                            final List<String> params = new ArrayList<>(List.of(String.valueOf(k), String.valueOf(v)));
                            if (millis > 0) {
                                params.addAll(List.of("PX", String.valueOf(millis)));
                            }
                            futures.add(api.set(params));
                        });
                        return CompositeFuture.all(Collections.unmodifiableList(futures));
                    })
                    .flatMap(ignored -> api.exec())
                    // null reply means transaction aborted
                    .map(Objects::nonNull)
                    .eventually(ignored -> connection.close())
                    .mapEmpty();
        });
    }

    @Override
    public Future<V> get(final K key) {
        LOG.info("REDIS: get {}", key);
        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            return api.get(String.valueOf(key))
                    .flatMap(value -> Future.succeededFuture((V) String.valueOf(value)))
                    .eventually(ignored -> connection.close());
        });
    }

    @Override
    public Future<Boolean> remove(final K key, final V value) {
        LOG.info("REDIS: remove {}={}", key, value);
        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            return api.watch(List.of(String.valueOf(key)))
                    .flatMap(ignored -> api.get(String.valueOf(key)))
                    .flatMap(response -> {
                        if (response == null) {
                            // key does not exist
                            return Future.succeededFuture(false);
                        }
                        if (String.valueOf(response).equals(value)) {
                            return api.multi()
                                    .flatMap(ignored -> api.del(List.of(String.valueOf(key))))
                                    .flatMap(ignored -> api.exec())
                                    // null reply means transaction aborted
                                    .map(Objects::nonNull);
                        } else {
                            return Future.succeededFuture(false);
                        }
                    })
                    .eventually(ignored -> connection.close());
        });
    }

    @Override
    public Future<Map<K, V>> getAll(final Set<? extends K> keys) {
        LOG.info("REDIS: getAll {}", keys.size());
        Objects.requireNonNull(client);

        return redis.connect().flatMap(connection -> {
            final RedisAPI api = RedisAPI.api(connection);
            final LinkedList<String> keyList = new LinkedList<>(keys.stream().map(String::valueOf).toList());
            keyList.forEach(i -> LOG.info("REDIS: Key: {}", i));
            final Map<K, V> result = new HashMap<>(keyList.size());
            return api.mget(keyList)
                    .flatMap(values -> {
                        LOG.info("REDIS: Got {} items back...", values.stream().toList().size());
                        values.forEach(i -> {
                            LOG.info("Iterating through result list: {}", i);
                            try {
                                if (i != null) { // TODO: this is kinda strange but some results are null and the BasicCache does not include those in the returned result. Ask about/investigate.
                                    result.put((K) keyList.removeFirst(), (V) i.toString());
                                } else {
                                    keyList.removeFirst();
                                }
                            } catch (Exception e) {
                                LOG.info(" - got exception {}", e.getMessage());
                            }
                        });
                        return Future.succeededFuture(result);
                    })
                    .eventually(ignored -> connection.close());
        });
    }
}
