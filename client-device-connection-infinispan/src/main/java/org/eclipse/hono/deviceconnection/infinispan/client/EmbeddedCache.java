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
import java.util.concurrent.atomic.AtomicBoolean;

import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An embedded cache.
 *
 * @param <K> The type of keys used by the cache.
 * @param <V> The type of values stored in the cache.
 */
public class EmbeddedCache<K, V> extends BasicCache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedCache.class);

    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private final EmbeddedCacheManager cacheManager;
    private final String cacheName;

    /**
     * Creates a new embedded cache instance.
     *
     * @param vertx The vert.x instance to run on.
     * @param cacheManager The connection to the cache.
     * @param cacheName The name of the cache.
     */
    public EmbeddedCache(
            final Vertx vertx,
            final EmbeddedCacheManager cacheManager,
            final String cacheName) {
        super(vertx, cacheManager);
        this.cacheManager = Objects.requireNonNull(cacheManager);
        this.cacheName = Objects.requireNonNull(cacheName);
    }

    @Override
    protected boolean isStarted() {
        return cacheManager.isRunning(cacheName) && getCache() != null;
    }

    @Override
    protected Future<Void> connectToCache() {

        if (connecting.compareAndSet(false, true)) {

            return vertx.executeBlocking(() -> {
                LOG.debug("trying to start cache manager");
                cacheManager.start();
                LOG.info("started cache manager");
                LOG.debug("trying to get cache");
                setCache(cacheManager.getCache(cacheName));
                if (isStarted()) {
                    LOG.info("successfully connected to cache");
                    return (Void) null;
                } else {
                    final var msg = "cache [%s] is not configured".formatted(cacheName);
                    LOG.debug("failed to connect to cache: {}", msg);
                    throw new IllegalStateException(msg);
                }
            })
            .onComplete(r -> {
                connecting.set(false);
            });
        } else {
            LOG.info("already trying to establish connection to cache");
            return Future.failedFuture("already trying to establish connection to cache");
        }
    }

    @Override
    public Future<JsonObject> checkForCacheAvailability() {

        if (isStarted()) {
            return Future.succeededFuture(new JsonObject());
        } else {
            // try to (re-)establish connection
            connectToCache();
            return Future.failedFuture("not connected to cache");
        }
    }
}
