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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.ReconnectListener;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.Futures;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An abstract base class for implementing caches.
 *
 * @param <K> The type of the key.
 * @param <V> The type of the value.
 */
public abstract class BasicCache<K, V> implements Cache<K, V>, ConnectionLifecycle<BasicCache<K, V>> {

    private static final Logger LOG = LoggerFactory.getLogger(BasicCache.class);

    protected final Vertx vertx;
    private final BasicCacheContainer cacheManager;
    private final K connectionCheckKey;
    private final V connectionCheckValue;

    private org.infinispan.commons.api.BasicCache<K, V> cache;

    /**
     * Create a new instance.
     *
     * @param vertx The vert.x instance to run on.
     * @param cacheManager The cache manager.
     * @param connectionCheckKey The key to use for checking the connection
     *        to the cache.
     * @param connectionCheckValue The value to use for checking the connection
     *        to the cache.
     */
    public BasicCache(
            final Vertx vertx,
            final BasicCacheContainer cacheManager,
            final K connectionCheckKey,
            final V connectionCheckValue) {
        this.vertx = Objects.requireNonNull(vertx);
        this.cacheManager = Objects.requireNonNull(cacheManager);
        this.connectionCheckKey = Objects.requireNonNull(connectionCheckKey);
        this.connectionCheckValue = Objects.requireNonNull(connectionCheckValue);
    }

    /**
     * Called to trigger connecting the cache.
     *
     * @return A future tracking the progress, never returns {@code null}.
     */
    protected abstract Future<Void> connectToGrid();

    /**
     * Checks if the cache manager is started.
     *
     * @return {@code true} if the cache manager is started, {@code false} otherwise.
     */
    protected abstract boolean isStarted();

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<BasicCache<K, V>> connect() {
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
        disconnect(r -> {
        });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {

        vertx.executeBlocking(r -> {
            try {
                cacheManager.stop();
                r.complete();
            } catch (final Throwable t) {
                r.fail(t);
            }
        }, (AsyncResult<Void> stopAttempt) -> {
            if (stopAttempt.succeeded()) {
                LOG.info("connection(s) to cache stopped successfully");
            } else {
                LOG.info("error trying to stop connection(s) to cache", stopAttempt.cause());
            }
            completionHandler.handle(stopAttempt);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDisconnectListener(final DisconnectListener<BasicCache<K, V>> listener) {
        // neither the embedded cache nor the Hotrod protocol does support signaling of connection loss
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addReconnectListener(final ReconnectListener<BasicCache<K, V>> listener) {
        // neither the embedded cache nor the Hotrod protocol does support signaling of connection loss
        // thus, there is no way to know when a connection has been re-established
    }

    protected void setCache(final org.infinispan.commons.api.BasicCache<K, V> cache) {
        this.cache = cache;
    }

    protected org.infinispan.commons.api.BasicCache<K, V> getCache() {
        return this.cache;
    }

    /**
     * Perform a task with a connected cache.
     * <p>
     * The method checks if the cache is connected. If it is, then it will call the
     * supplier, providing a <em>non-null</em> cache instance.
     * <p>
     * If the cache is not connected, the supplier will not be called and instead
     * it will return a failed future, provided be {@link #noConnectionFailure()}.
     *
     * @param <T> The type of the return value.
     * @param futureSupplier The supplier, providing the operation which should be performed.
     * @return The future, tracking the result of the operation.
     */
    protected <T> Future<T> withCache(final Function<org.infinispan.commons.api.BasicCache<K, V>, CompletionStage<T>> futureSupplier) {

        final var cache = this.cache;

        if (cache == null) {
            return noConnectionFailure();
        } else {
            return Futures.create(() -> futureSupplier.apply(cache));
        }

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

        return withCache(cache -> cache.putAsync(key, value));

    }

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
     */
    @Override
    public Future<V> put(final K key, final V value, final long lifespan, final TimeUnit lifespanUnit) {

        return withCache(cache -> cache.putAsync(key, value, lifespan, lifespanUnit));

    }

    /**
     * Remove a key/value mapping from the cache.
     *
     * @param key The key.
     * @param value The value.
     * @return {@code true} if the key was mapped to the value, {@code false}
     *         otherwise.
     */
    @Override
    public Future<Boolean> remove(final K key, final V value) {

        return withCache(cache -> cache.removeAsync(key, value));

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

        return withCache(cache -> cache.getAsync(key));

    }

    /**
     * Gets the values for the specified keys from the cache.
     *
     * @param keys The keys.
     * @return A succeeded future containing a map with key/value pairs.
     */
    @Override
    public Future<Map<K, V>> getAll(final Set<? extends K> keys) {

        return withCache(cache -> cache.getAllAsync(keys));

    }

    /**
     * Returns a failed future, reporting a missing connection to the cache.
     *
     * @param <V> The value type of the returned future.
     * @return A failed future, never returns {@code null}.
     */
    private static <V> Future<V> noConnectionFailure() {

        return Future.failedFuture(new ServerErrorException(
                HttpURLConnection.HTTP_UNAVAILABLE, "no connection to data grid"));

    }

    /**
     * Checks if the cache is connected.
     *
     * @return A future that is completed with information about a successful check's result.
     *         Otherwise, the future will be failed with a {@link ServerErrorException}.
     */
    @Override
    public Future<JsonObject> checkForCacheAvailability() {

        final Promise<JsonObject> result = Promise.promise();

        if (!isStarted()) {
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

}
