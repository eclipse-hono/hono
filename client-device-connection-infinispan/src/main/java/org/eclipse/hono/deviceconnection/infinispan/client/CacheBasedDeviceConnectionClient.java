/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.DeviceConnectionClient;

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;


/**
 * A client for accessing device connection information in a data grid.
 */
public final class CacheBasedDeviceConnectionClient implements DeviceConnectionClient {

    final String tenantId;
    final DeviceConnectionInfo cache;

    /**
     * Creates a client for accessing device connection information.
     *
     * @param tenantId The tenant that this client is scoped to.
     * @param cache The remote cache that contains the data.
     */
    public CacheBasedDeviceConnectionClient(final String tenantId, final DeviceConnectionInfo cache) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.cache = Objects.requireNonNull(cache);
    }

    /**
     * {@inheritDoc}
     *
     * The given handler will immediately be invoked with a succeeded result.
     */
    @Override
    public void close(final Handler<AsyncResult<Void>> closeHandler) {
        closeHandler.handle(Future.succeededFuture());
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true} if this client is connected to the data grid.
     */
    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * Invocations of this method are ignored.
     */
    @Override
    public void setRequestTimeout(final long timoutMillis) {
        // ignored
    }

    /**
     * {@inheritDoc}
     *
     * @return Always 1.
     */
    @Override
    public int getCredit() {
        return 1;
    }

    /**
     * {@inheritDoc}
     *
     * The given handler will be invoked immediately.
     */
    @Override
    public void sendQueueDrainHandler(final Handler<Void> handler) {
        handler.handle(null);
    }

    /**
     * {@inheritDoc}
     *
     * If this method is invoked from a vert.x Context, then the returned future will be completed on that context.
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(final String deviceId, final String gatewayId, final SpanContext context) {
        return cache.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(final String deviceId, final SpanContext context) {
        return cache.getLastKnownGatewayForDevice(tenantId, deviceId, context);
    }

    @Override
    public Future<Void> setCommandHandlingAdapterInstance(final String deviceId, final String adapterInstanceId,
            final Duration lifespan, final boolean updateOnly, final SpanContext context) {
        return cache.setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, lifespan, updateOnly, context);
    }

    @Override
    public Future<Boolean> removeCommandHandlingAdapterInstance(final String deviceId, final String adapterInstanceId,
            final SpanContext context) {
        return cache.removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, context);
    }

    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(final String deviceId, final List<String> viaGateways,
            final SpanContext context) {
        return cache.getCommandHandlingAdapterInstances(tenantId, deviceId, new HashSet<>(viaGateways), context);
    }
}
