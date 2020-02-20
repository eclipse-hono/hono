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
import org.springframework.beans.factory.annotation.Autowired;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;


/**
 * A factory for creating Device Connection service clients that connect directly to
 * an Infinispan cluster for reading and writing device connection information.
 *
 */
public final class HotrodBasedDeviceConnectionClientFactory implements BasicDeviceConnectionClientFactory, ConnectionLifecycle<HotrodCache<String, String>> {

    private final Cache<String, HotrodBasedDeviceConnectionClient> clients = Caffeine.newBuilder()
            .maximumSize(100)
            .build();
    private final HotrodCache<String, String> cache;

    /**
     * @param cache The cache representing the data grid.
     * @throws NullPointerException if cache is {@code null}.
     */
    public HotrodBasedDeviceConnectionClientFactory(@Autowired final HotrodCache<String, String> cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<HotrodCache<String, String>> connect() {
        return cache.connect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDisconnectListener(final DisconnectListener<HotrodCache<String, String>> listener) {
        cache.addDisconnectListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addReconnectListener(final ReconnectListener<HotrodCache<String, String>> listener) {
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
            final DeviceConnectionInfo info = new HotrodBasedDeviceConnectionInfo(cache);
            return new HotrodBasedDeviceConnectionClient(key, info);
        });
        return Future.succeededFuture(result);
    }

}
