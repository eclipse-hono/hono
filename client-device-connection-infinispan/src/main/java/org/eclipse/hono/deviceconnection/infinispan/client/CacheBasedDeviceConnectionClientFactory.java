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

import org.eclipse.hono.client.BasicDeviceConnectionClientFactory;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.ReconnectListener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;


/**
 * A factory for creating Device Connection service clients that connect directly to
 * an Infinispan cluster for reading and writing device connection information.
 *
 */
public final class CacheBasedDeviceConnectionClientFactory implements BasicDeviceConnectionClientFactory, ConnectionLifecycle<BasicCache<String, String>> {

    private final Cache<String, CacheBasedDeviceConnectionClient> clients = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
    private final BasicCache<String, String> cache;
    private final Tracer tracer;

    /**
     * @param cache The cache representing the data grid.
     * @param tracer The OpenTracing {@code Tracer} to use for tracking requests done by clients created by this factory.
     * @throws NullPointerException if cache or tracer is {@code null}.
     */
    public CacheBasedDeviceConnectionClientFactory(final BasicCache<String, String> cache, final Tracer tracer) {
        this.cache = Objects.requireNonNull(cache);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<BasicCache<String, String>> connect() {
        return cache.connect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDisconnectListener(final DisconnectListener<BasicCache<String, String>> listener) {
        cache.addDisconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addReconnectListener(final ReconnectListener<BasicCache<String, String>> listener) {
        cache.addReconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> isConnected() {
        return cache.isConnected();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect() {
        cache.disconnect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        cache.disconnect(completionHandler);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException if the factory is not connected to the data grid.
     */
    @Override
    public Future<DeviceConnectionClient> getOrCreateDeviceConnectionClient(final String tenantId) {
        final DeviceConnectionClient result = clients.get(tenantId, key -> {
            final DeviceConnectionInfo info = new CacheBasedDeviceConnectionInfo(cache, tracer);
            return new CacheBasedDeviceConnectionClient(key, info);
        });
        return Future.succeededFuture(result);
    }

}
