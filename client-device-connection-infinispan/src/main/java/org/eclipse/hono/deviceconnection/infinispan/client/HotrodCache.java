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

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.Futures;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.commons.api.BasicCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A remote cache that connects to a data grid using the Hotrod protocol.
 *
 * @param <K> The type of keys used by the cache.
 * @param <V> The type of values stored in the cache.
 */
public final class HotrodCache<K, V> implements RemoteCache<K, V>, ConnectionLifecycle<HotrodCache<K, V>> {

    private static final Logger LOG = LoggerFactory.getLogger(HotrodCache.class);

    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final Vertx vertx;
    private final RemoteCacheContainer cacheManager;
    private final String cacheName;
    private final K connectionCheckKey;
    private final V connectionCheckValue;

    private org.infinispan.client.hotrod.RemoteCache<K, V> cache;

    /**
     * @param vertx The vert.x instance to run on.
     * @param cacheManager The connection to the remote cache.
     * @param name The name of the (remote) cache.
     * @param connectionCheckKey The key to use for checking the connection
     *                           to the data grid.
     * @param connectionCheckValue The value to use for checking the connection
     *                           to the data grid.
     */
    public HotrodCache(
            final Vertx vertx,
            final RemoteCacheContainer cacheManager,
            final String name,
            final K connectionCheckKey,
            final V connectionCheckValue) {
        this.vertx = Objects.requireNonNull(vertx);
        this.cacheManager = Objects.requireNonNull(cacheManager);
        this.cacheName = Objects.requireNonNull(name);
        this.connectionCheckKey = Objects.requireNonNull(connectionCheckKey);
        this.connectionCheckValue = Objects.requireNonNull(connectionCheckValue);
    }

    public BasicCache<K, V> getCache() {
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<HotrodCache<K, V>> connect() {
        return connectToGrid().map(ok -> this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> isConnected() {
        return checkForCacheAvailability().mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect() {
        disconnect(r -> {});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {

        vertx.executeBlocking(r -> {
            try {
                if (cacheManager != null) {
                    cacheManager.stop();
                }
                r.complete();
            } catch (final Throwable t) {
                r.fail(t);
            }
        }, (AsyncResult<Void> stopAttempt) -> {
            if (stopAttempt.succeeded()) {
                LOG.info("connection(s) to remote cache stopped successfully");
            } else {
                LOG.info("error trying to stop connection(s) to remote cache", stopAttempt.cause());
            }
            completionHandler.handle(stopAttempt);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDisconnectListener(final DisconnectListener<HotrodCache<K, V>> listener) {
        // the Hotrod protocol does not support signaling of connection loss
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addReconnectListener(final ReconnectListener<HotrodCache<K, V>> listener) {
        // the Hotrod protocol does not support signaling of connection loss
        // thus, there is no way to know when a connection has been re-established
    }

    /**
     * Puts a value to the cache.
     *
     * @param key The key.
     * @param value The value.
     * @return A succeeded future containing the previous value or {@code null} if the
     *         cache didn't contain the key yet.
     *         A failed future if the value could not be stored in the cache.
     */
    @Override
    public Future<V> put(final K key, final V value) {

        if (cache == null) {

            return noConnectionFailure();

        } else {

            return Futures.create(() -> {
                return cache
                    .withFlags(Flag.FORCE_RETURN_VALUE)
                    .putAsync(key, value);
            });

        }

    }

    @Override
    public Future<Boolean> removeWithVersion(final K key, final long version) {

        if (cache == null) {

            return noConnectionFailure();

        } else {

            return Futures.create(() -> {
                return cache
                    .withFlags(Flag.FORCE_RETURN_VALUE)
                    .removeWithVersionAsync(key, version);
            });

        }

    }

    /**
     * Gets a value from the cache.
     *
     * @param key The key.
     * @return A succeeded future containing the value or {@code null} if the
     *         cache didn't contain the key yet.
     *         A failed future if the value could not be read from the cache.
     */
    @Override
    public Future<V> get(final K key) {

        if (cache == null) {

            return noConnectionFailure();

        } else {

            return Futures.create(() -> {
                return cache
                    .getAsync(key);
            });

        }

    }

    /**
     * Gets a versioned value from the cache.
     *
     * @param key The key.
     * @return A succeeded future containing the versioned value or {@code null} if the
     *         cache didn't contain the key yet.
     *         A failed future if the value could not be read from the cache.
     */
    @Override
    public Future<Versioned<V>> getWithVersion(final K key) {

        if (cache == null) {

            return noConnectionFailure();

        } else {

            return Futures.create(() -> {
                return cache
                    .getWithMetadataAsync(key)
                    .thenApply(value -> {
                        if (value != null ) {
                            return new Versioned<>(value.getVersion(), value.getValue());
                        } else {
                            return null;
                        }
                    });
            });

        }

    }

    @Override
    public Future<Map<K, V>> getAll(final Set<? extends K> keys) {

        if (cache == null) {

            return noConnectionFailure();

        } else {

            return Futures.create(() -> {
                return cache
                    .getAllAsync(keys);
            });

        }

    }

    private Future<Void> connectToGrid() {

        final Promise<Void> result = Promise.promise();

        if (connecting.compareAndSet(false, true)) {

            vertx.executeBlocking(r -> {
                try {
                    if (!cacheManager.isStarted()) {
                        LOG.debug("trying to start cache manager");
                        cacheManager.start();
                        LOG.info("started cache manager, now connecting to remote cache");
                    }
                    LOG.debug("trying to connect to remote cache");
                    cache = cacheManager.getCache(cacheName, cacheManager.getConfiguration().forceReturnValues());
                    if (cache == null) {
                        r.fail(new IllegalStateException("remote cache [" + cacheName + "] does not exist"));
                    } else {
                        cache.start();
                        r.complete(cache);
                    }
                } catch (final Throwable t) {
                    r.fail(t);
                }
            }, attempt -> {
                if (attempt.succeeded()) {
                    LOG.info("successfully connected to remote cache");
                    result.complete();
                } else {
                    LOG.debug("failed to connect to remote cache: {}", attempt.cause().getMessage());
                    result.fail(attempt.cause());
                }
                connecting.set(false);
            });
        } else {
            LOG.info("already trying to establish connection to data grid");
            result.fail("already trying to establish connection to data grid");
        }
        return result.future();
    }

    /**
     * Checks if the cache is connected to the data grid.
     *
     * @return A future that is completed with information about a successful check's result.
     *         Otherwise, the future will be failed with a {@link ServerErrorException}.
     */
    @Override
    public Future<JsonObject> checkForCacheAvailability() {

        final Promise<JsonObject> result = Promise.promise();

        if (cacheManager.isStarted() && cache != null) {
            final Instant start = Instant.now();
            put(connectionCheckKey, connectionCheckValue)
                .setHandler(r -> {
                    if (r.succeeded()) {
                        final long requestDuration = Duration.between(start, Instant.now()).toMillis();
                        result.complete(new JsonObject().put("grid-response-time", requestDuration));
                    } else {
                        LOG.debug("failed to put test value to cache", r.cause());
                        result.fail(r.cause());
                    }
                });
        } else {
            // try to (re-)establish connection
            connectToGrid();
            result.fail("not connected to data grid");
        }
        return result.future();
    }

    /**
     * Returns a failed future, reporting a missing connection to the data grid.
     *
     * @param <V> The value type of the returned future.
     * @return A failed future. Never returns {@code null}.
     */
    private static <V> Future<V> noConnectionFailure() {
        return Future.failedFuture(new ServerErrorException(
                HttpURLConnection.HTTP_UNAVAILABLE, "no connection to data grid"));
    }

}
