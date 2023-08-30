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

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;

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
        final Promise<Void> promise = Promise.promise();
        /*
        createRedisClient()
                .onSuccess(c -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
         */
        redis = Redis.createClient(vertx, properties);
        redis.connect()
                .onSuccess(c -> client = c)
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Void> stop() {
        LOG.info("REDIS: stopping cache");
        redis.close();
        return Future.succeededFuture();
    }

    /*
     * Will create a redis client and set up a reconnect handler when there is an exception in the connection.
     *
    private Future<RedisConnection> createRedisClient() {
        final Promise<RedisConnection> promise = Promise.promise();

        // make sure to invalidate old connection if present
        if (redis != null) {
            redis.close();;
        }

        if (CONNECTING.compareAndSet(false, true)) {
            redis = Redis.createClient(vertx, properties);
            redis
                    .connect()
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
     */

    @Override
    public Future<JsonObject> checkForCacheAvailability() {
        LOG.info("REDIS: checking for cache availability");

        Objects.requireNonNull(client);

        final Promise<JsonObject> promise = Promise.promise();
        final RedisAPI api = RedisAPI.api(client);
        api.ping(List.of())
                .onSuccess(v -> promise.complete(new JsonObject()))
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Void> put(final K key, final V value) {
        LOG.info("REDIS: put {}={}", key, value);
        final Promise<Void> promise = Promise.promise();
        final RedisAPI api = RedisAPI.api(client);
        api.set(List.of(key.toString(), value.toString()))
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Void> put(final K key, final V value, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("REDIS: put {}={} ({} {})", key, value, lifespan, lifespanUnit);
        final long millis = lifespanUnit.toMillis(lifespan);
        final Promise<Void> promise = Promise.promise();
        final RedisAPI api = RedisAPI.api(client);
        final List<String> params = new ArrayList<>(List.of(key.toString(), value.toString()));
        if (millis > 0) {
            params.addAll(List.of("PX", String.valueOf(millis)));
        }
        api.set(params)
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data) {
        LOG.info("REDIS: putAll ({})", data.size());
        final Promise<Void> promise = Promise.promise();
        final RedisAPI api = RedisAPI.api(client);
        final List<String> keyValues = new ArrayList<>(data.size() * 2);
        data.forEach((k, v) -> {
            keyValues.add(k.toString());
            keyValues.add(v.toString()); });
        api.mset(keyValues)
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data, final long lifespan, final TimeUnit lifespanUnit) {
        LOG.info("REDIS: putAll ({}) ({} {})", data.size(), lifespan, lifespanUnit);
        final Promise<Void> promise = Promise.promise();
        final long millis = lifespanUnit.toMillis(lifespan);
        final RedisAPI api = RedisAPI.api(client);
        api.multi();
        data.forEach((k, v) -> {
            final List<String> params = new ArrayList<>(List.of(k.toString(), v.toString()));
            if (millis > 0) {
                params.addAll(List.of("PX", String.valueOf(millis)));
            }
            api.set(params);
        });
        api.exec()
                .onSuccess(v -> promise.complete())
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<V> get(final K key) {
        LOG.info("REDIS: get {}", key);
        final Promise<V> promise = Promise.promise();
        final RedisAPI api = RedisAPI.api(client);
        api.get(key.toString())
                .onSuccess(v -> promise.complete((V) v) )
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Boolean> remove(final K key, final V value) {
        LOG.info("REDIS: remove {}={}", key, value);
        //TODO: why is the value being passed here? Do we need to use that?
        final Promise<Boolean> promise = Promise.promise();
        final RedisAPI api = RedisAPI.api(client);
        api.del(List.of(key.toString()))
                .onSuccess(v -> promise.complete(true))
                .onFailure(promise::fail);
        return promise.future();
    }

    @Override
    public Future<Map<K, V>> getAll(final Set<? extends K> keys) {
        LOG.info("REDIS: getAll {}", keys.size());
        final Promise<Map<K, V>> promise = Promise.promise();

        final RedisAPI api = RedisAPI.api(client);
        // Make sure the keys are in order and we can pop off the front
        final LinkedList<String> keyList = new LinkedList<>(keys.stream().map(String::valueOf).toList());
        keyList.forEach(i -> LOG.info("REDIS: Item: {}", i));
        final Map<K, V> result = new HashMap<>(keyList.size());
        api.mget(keyList)
                .onComplete(v -> {
                    LOG.info("REDIS: Got {} items back...", v.result().stream().toList().size());
                    v.result().forEach(i -> {
                        LOG.info("Iterating through result list: {}", i);
                        try {
                            if (i != null) { // TODO: this is kinda strange but some results are null and the BasicCache does not include those in the returned result. Ask about/investigate.
                                result.put((K) keyList.removeFirst(), (V) i.toString());
                            }
                        } catch (Exception e) {
                            LOG.info(" - got exception {}", e.getMessage());
                        }
                    });
                    promise.complete(result);
                })
                .onFailure(promise::fail);
        return promise.future();
    }
}
