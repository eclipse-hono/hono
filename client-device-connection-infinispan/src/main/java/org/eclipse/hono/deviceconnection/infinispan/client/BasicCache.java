/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.Futures;
import org.eclipse.hono.util.Lifecycle;
import org.infinispan.commons.api.BasicCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * An abstract base class for implementing caches based on an
 * Infinispan {@link org.infinispan.commons.api.BasicCache}.
 *
 * @param <K> The type of the key.
 * @param <V> The type of the value.
 */
public abstract class BasicCache<K, V> implements Cache<K, V>, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(BasicCache.class);

    /**
     * The vert.x instance that this cache runs on.
     */
    protected final Vertx vertx;
    private final BasicCacheContainer cacheManager;
    private final AtomicBoolean stopCalled = new AtomicBoolean();

    private org.infinispan.commons.api.BasicCache<K, V> cache;

    /**
     * Creates a new instance.
     *
     * @param vertx The vert.x instance to run on.
     * @param cacheManager The cache manager.
     */
    protected BasicCache(final Vertx vertx, final BasicCacheContainer cacheManager) {
        this.vertx = Objects.requireNonNull(vertx);
        this.cacheManager = Objects.requireNonNull(cacheManager);
    }

    /**
     * Called to trigger connecting the cache.
     *
     * @return A future tracking the progress, never returns {@code null}.
     */
    protected abstract Future<Void> connectToCache();

    /**
     * Checks if the cache manager is started.
     *
     * @return {@code true} if the cache manager is started, {@code false} otherwise.
     */
    protected abstract boolean isStarted();

    @Override
    public Future<Void> start() {
        LOG.info("starting cache");
        return connectToCache();
    }

    @Override
    public Future<Void> stop() {
        if (!stopCalled.compareAndSet(false, true)) {
            return Future.succeededFuture();
        }
        LOG.info("stopping cache");
        setCache(null);
        final Promise<Void> result = Promise.promise();
        vertx.executeBlocking(r -> {
            try {
                cacheManager.stop();
                r.complete();
            } catch (final Exception t) {
                r.fail(t);
            }
        }, (AsyncResult<Void> stopAttempt) -> {
            if (stopAttempt.succeeded()) {
                LOG.info("connection(s) to cache stopped successfully");
            } else {
                LOG.info("error trying to stop connection(s) to cache", stopAttempt.cause());
            }
            result.handle(stopAttempt);
        });
        return result.future();
    }

    protected void setCache(final org.infinispan.commons.api.BasicCache<K, V> cache) {
        this.cache = cache;
    }

    protected org.infinispan.commons.api.BasicCache<K, V> getCache() {
        return this.cache;
    }

    /**
     * Performs a task with a connected cache.
     * <p>
     * The method checks if the cache instance has been set. If that is the case, then the
     * supplier will be invoked, providing a <em>non-null</em> cache instance.
     * <p>
     * If the cache has not been set (yet) or it has been stopped, the supplier will not be
     * called and a failed future will be returned, provided by {@link #noConnectionFailure()}.
     *
     * @param <T> The type of the return value.
     * @param futureSupplier The supplier, providing the operation which should be invoked.
     * @return The future, tracking the result of the operation.
     */
    protected final <T> Future<T> withCache(
            final Function<org.infinispan.commons.api.BasicCache<K, V>, CompletionStage<T>> futureSupplier) {

        return Optional.ofNullable(cache)
                .map(c -> Futures.create(() -> futureSupplier.apply(c)))
                .orElseGet(BasicCache::noConnectionFailure)
                .onComplete(this::postCacheAccess);
    }

    /**
     * Performs extra processing on the result of a cache operation returned by {@link #withCache(Function)}.
     * <p>
     * Subclasses should override this method if needed.
     * <p>
     * This default implementation does nothing.
     *
     * @param <T> The type of the return value.
     * @param cacheOperationResult The result of the cache operation.
     */
    protected  <T> void postCacheAccess(final AsyncResult<T> cacheOperationResult) {
        // nothing done by default
    }

    @Override
    public Future<Void> put(final K key, final V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return withCache(aCache -> aCache.putAsync(key, value).thenApply(v -> null));
    }

    @Override
    public Future<Void> put(final K key, final V value, final long lifespan, final TimeUnit lifespanUnit) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        Objects.requireNonNull(lifespanUnit);

        return withCache(aCache -> aCache.putAsync(key, value, lifespan, lifespanUnit).thenApply(v -> null));
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data) {
        Objects.requireNonNull(data);

        return withCache(aCache -> aCache.putAllAsync(data));
    }

    @Override
    public Future<Void> putAll(final Map<? extends K, ? extends V> data, final long lifespan, final TimeUnit lifespanUnit) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(lifespanUnit);

        return withCache(aCache -> aCache.putAllAsync(data, lifespan, lifespanUnit));
    }

    @Override
    public Future<Boolean> remove(final K key, final V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return withCache(aCache -> aCache.removeAsync(key, value));
    }

    @Override
    public Future<V> get(final K key) {
        Objects.requireNonNull(key);

        return withCache(aCache -> aCache.getAsync(key));
    }

    @Override
    public Future<Map<K, V>> getAll(final Set<? extends K> keys) {
        Objects.requireNonNull(keys);

        return withCache(aCache -> aCache.getAllAsync(keys));
    }

    /**
     * Returns a failed future, reporting a missing connection to the cache.
     *
     * @param <V> The value type of the returned future.
     * @return A failed future, never returns {@code null}.
     */
    protected static <V> Future<V> noConnectionFailure() {

        return Future.failedFuture(new ServerErrorException(
                HttpURLConnection.HTTP_UNAVAILABLE, "no connection to data grid"));
    }

}
