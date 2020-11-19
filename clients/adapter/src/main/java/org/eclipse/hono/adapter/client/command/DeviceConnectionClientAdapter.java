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


package org.eclipse.hono.adapter.client.command;

import java.time.Duration;
import java.util.Objects;

import org.eclipse.hono.adapter.client.util.ServiceClient;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * An adapter that maps CommandRouterClient method invocations to a
 * {@link DeviceConnectionClient}.
 *
 */
public final class DeviceConnectionClientAdapter implements CommandRouterClient, ServiceClient {

    private final DeviceConnectionClient deviceConnectionClient;

    /**
     * Creates a new adapter for a Device Connection service client.
     *
     * @param client The client that needs to be adapted.
     * @throws NullPointerException if client is {@code null}.
     */
    public DeviceConnectionClientAdapter(final DeviceConnectionClient client) {
        this.deviceConnectionClient = Objects.requireNonNull(client);
    }


    @Override
    public Future<Void> stop() {
        return deviceConnectionClient.stop();
    }

    @Override
    public Future<Void> start() {
        return deviceConnectionClient.start();
    }

    @Override
    public Future<Void> registerCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {
        return deviceConnectionClient.setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, lifespan, context);
    }

    @Override
    public Future<Void> unregisterCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {
        return deviceConnectionClient.removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, context);
    }

    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {
        return deviceConnectionClient.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        if (deviceConnectionClient instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionClient).registerReadinessChecks(readinessHandler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        if (deviceConnectionClient instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionClient).registerLivenessChecks(livenessHandler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
                .append("{deviceConnectionClient: ")
                .append(deviceConnectionClient)
                .append("]}")
                .toString();
    }
}
