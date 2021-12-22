/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A remote cache that connects to a data grid using the Hotrod protocol.
 *
 * @param <K> The type of keys used by the cache.
 * @param <V> The type of values stored in the cache.
 */
public final class HotrodCache<K, V> extends BasicCache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(HotrodCache.class);

    /**
     * Maximum age for a cached connection check result to be used in {@link #checkForCacheAvailability()}.
     */
    private static final Duration CACHED_CONNECTION_CHECK_RESULT_MAX_AGE = Duration.ofSeconds(30);

    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final RemoteCacheContainer cacheManager;
    private final String cacheName;

    private final K connectionCheckKey;
    private final V connectionCheckValue;

    private ConnectionCheckResult lastConnectionCheckResult;

    /**
     * Creates a new HotrodCache instance.
     *
     * @param vertx The vert.x instance to run on.
     * @param cacheManager The connection to the remote cache.
     * @param cacheName The name of the (remote) cache.
     * @param connectionCheckKey The key to use for checking the connection
     *                           to the data grid.
     * @param connectionCheckValue The value to use for checking the connection
     *                           to the data grid.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    HotrodCache(
            final Vertx vertx,
            final RemoteCacheContainer cacheManager,
            final String cacheName,
            final K connectionCheckKey,
            final V connectionCheckValue) {
        super(vertx, cacheManager);
        this.cacheManager = Objects.requireNonNull(cacheManager);
        this.cacheName = Objects.requireNonNull(cacheName);
        this.connectionCheckKey = Objects.requireNonNull(connectionCheckKey);
        this.connectionCheckValue = Objects.requireNonNull(connectionCheckValue);
    }

    /**
     * Creates a new remote cache.
     *
     * @param vertx The vert.x instance to run on.
     * @param properties The remote cache configuration.
     * @param commonCacheConfig The common cache configuration.
     * @return The remote cache.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static HotrodCache<String, String> from(
            final Vertx vertx,
            final InfinispanRemoteConfigurationProperties properties,
            final CommonCacheConfig commonCacheConfig) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(properties);
        Objects.requireNonNull(commonCacheConfig);

        final var configBuilder = properties.getConfigurationBuilder();
        configBuilder.marshaller(new ProtoStreamMarshaller());
        final var configuration = configBuilder.build();
        if (LOG.isInfoEnabled()) {
            LOG.info("creating HotrodCache using configuration: {}", configuration);
        }
        return new HotrodCache<>(
                vertx,
                new RemoteCacheManager(configuration, false),
                commonCacheConfig.getCacheName(),
                commonCacheConfig.getCheckKey(),
                commonCacheConfig.getCheckValue());
    }

    @Override
    protected Future<Void> connectToCache() {

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
                    @SuppressWarnings("unchecked")
                    final var cache = (RemoteCache<K, V>) cacheManager.getCache(cacheName);
                    if (cache == null) {
                        r.fail(new IllegalStateException("remote cache [" + cacheName + "] does not exist"));
                    } else {
                        cache.start();
                        setCache(cache);
                        r.complete(cache);
                    }
                } catch (final Exception t) {
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

    @Override
    protected boolean isStarted() {
        return cacheManager.isStarted() && getCache() != null;
    }

    @Override
    protected <T> void postCacheAccess(final AsyncResult<T> cacheOperationResult) {
        lastConnectionCheckResult = new ConnectionCheckResult(cacheOperationResult.cause());
    }

    /**
     * Checks if the cache is connected.
     *
     * @return A future that is completed with information about a successful check's result.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ServerErrorException}.
     */
    @Override
    public Future<JsonObject> checkForCacheAvailability() {

        if (isStarted()) {
            final ConnectionCheckResult lastResult = lastConnectionCheckResult;
            if (lastResult != null && !lastResult.isOlderThan(CACHED_CONNECTION_CHECK_RESULT_MAX_AGE)) {
                return lastResult.asFuture();
            } else {
                final Promise<JsonObject> result = Promise.promise();
                put(connectionCheckKey, connectionCheckValue)
                        .onComplete(r -> {
                            if (r.succeeded()) {
                                result.complete(new JsonObject());
                            } else {
                                LOG.debug("failed to put test value to cache", r.cause());
                                result.fail(r.cause());
                            }
                        });
                return result.future();
            }
        } else {
            // try to (re-)establish connection
            connectToCache();
            return Future.failedFuture("not connected to data grid");
        }
    }

    /**
     * Keeps the result of a connection check.
     */
    private static class ConnectionCheckResult {
        private final Instant creationTimestamp = Instant.now();
        private final Throwable errorResult;

        /**
         * Creates a new ConnectionCheckResult.
         *
         * @param errorResult The error in case the check failed; use {@code null} if the check succeeded.
         */
        ConnectionCheckResult(final Throwable errorResult) {
            this.errorResult = errorResult;
        }

        /**
         * Checks if the result is older than the given time span, determined from the current point in time.
         *
         * @param timespan The time span.
         * @return {@code true} if the result is older.
         */
        public boolean isOlderThan(final Duration timespan) {
            return creationTimestamp.isBefore(Instant.now().minus(timespan));
        }

        /**
         * Gets a future indicating the connection check outcome.
         *
         * @return A succeeded future if the check succeeded, otherwise a failed future.
         */
        public Future<JsonObject> asFuture() {
            return errorResult != null ? Future.failedFuture(errorResult) : Future.succeededFuture(new JsonObject());
        }
    }

}
