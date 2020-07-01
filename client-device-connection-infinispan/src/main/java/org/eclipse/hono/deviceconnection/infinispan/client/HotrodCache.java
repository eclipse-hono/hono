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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A remote cache that connects to a data grid using the Hotrod protocol.
 *
 * @param <K> The type of keys used by the cache.
 * @param <V> The type of values stored in the cache.
 */
public final class HotrodCache<K, V> extends BasicCache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(HotrodCache.class);

    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final RemoteCacheContainer cacheManager;
    private final String cacheName;

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
        super(vertx, cacheManager, connectionCheckKey, connectionCheckValue);
        this.cacheManager = Objects.requireNonNull(cacheManager);
        this.cacheName = Objects.requireNonNull(name);
    }

    @Override
    protected Future<Void> connectToGrid() {

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
                    setCache(cacheManager.getCache(cacheName));
                    if (getCache() == null) {
                        r.fail(new IllegalStateException("remote cache [" + cacheName + "] does not exist"));
                    } else {
                        getCache().start();
                        r.complete(getCache());
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

    @Override
    protected boolean isStarted() {
        return cacheManager.isStarted() && getCache() != null;
    }

    // Method overridden because RemoteCache#removeAsync(key, value) throws an UnsupportedOperationException.
    @Override
    public Future<Boolean> remove(final K key, final V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        return withCache(cache -> {
            final RemoteCache<K, V> remoteCache = (RemoteCache<K, V>) cache;
            return remoteCache.getWithMetadataAsync(key).thenCompose(metadataValue -> {
                if (metadataValue != null && value.equals(metadataValue.getValue())) {
                    // If removeWithVersionAsync() returns false here (meaning that the value was updated in between),
                    // the updated value shall prevail and no new removal attempt with a new getWithMetadataAsync() invocation will be done.
                    return remoteCache.removeWithVersionAsync(key, metadataValue.getVersion());
                } else {
                    return CompletableFuture.completedFuture(Boolean.FALSE);
                }
            });
        });
    }

}
