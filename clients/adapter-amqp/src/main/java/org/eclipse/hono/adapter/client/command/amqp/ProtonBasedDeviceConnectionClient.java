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


package org.eclipse.hono.adapter.client.command.amqp;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.client.amqp.AbstractRequestResponseClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler.Factory;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.client.impl.CredentialsClientImpl;
import org.eclipse.hono.client.impl.DeviceConnectionClientImpl;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;


/**
 * A vertx-proton based client for accessing Hono's <em>Device Connection</em> API.
 *
 */
public class ProtonBasedDeviceConnectionClient extends AbstractRequestResponseClient<DeviceConnectionResult>
        implements DeviceConnectionClient {

    private final CachingClientFactory<org.eclipse.hono.client.DeviceConnectionClient> clientFactory;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Device Connection service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters other than the cache provider are {@code null}.
     */
    public ProtonBasedDeviceConnectionClient(
            final HonoConnection connection,
            final Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {

        super(connection, samplerFactory, adapterConfig, null);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), org.eclipse.hono.client.DeviceConnectionClient::isOpen);
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String address = CredentialsClientImpl.getTargetAddress(msg.body());
        Optional.ofNullable(clientFactory.getClient(address))
            .ifPresent(client -> client.close(v -> clientFactory.removeClient(address)));
    }

    private Future<org.eclipse.hono.client.DeviceConnectionClient> getOrCreateDeviceConnectionClient(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            DeviceConnectionClientImpl.getTargetAddress(tenantId),
                            () -> DeviceConnectionClientImpl.create(
                                    connection,
                                    tenantId,
                                    samplerFactory.create(DeviceConnectionConstants.DEVICE_CONNECTION_ENDPOINT),
                                    this::removeDeviceConnectionClient,
                                    this::removeDeviceConnectionClient),
                            result);
                }));
    }

    private void removeDeviceConnectionClient(final String tenantId) {
        clientFactory.removeClient(DeviceConnectionClientImpl.getTargetAddress(tenantId));
    }

    /**
     * {@inheritDoc}
     *
     * Clears the state of the client factory.
     */
    @Override
    protected void onDisconnect() {
        clientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getLastKnownGatewayForDevice(
            final String tenant,
            final String deviceId,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);

        return getOrCreateDeviceConnectionClient(tenant)
                .compose(client -> client.getLastKnownGatewayForDevice(deviceId, context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> setLastKnownGatewayForDevice(
            final String tenant,
            final String deviceId,
            final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        return getOrCreateDeviceConnectionClient(tenant)
                .compose(client -> client.setLastKnownGatewayForDevice(deviceId, gatewayId, context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> setCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        return getOrCreateDeviceConnectionClient(tenantId)
                .compose(client -> client.setCommandHandlingAdapterInstance(deviceId, adapterInstanceId, lifespan, context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> removeCommandHandlingAdapterInstance(
            final String tenantId,
            final String deviceId,
            final String adapterInstanceId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        return getOrCreateDeviceConnectionClient(tenantId)
                .compose(client -> client.removeCommandHandlingAdapterInstance(deviceId, adapterInstanceId, context));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<JsonObject> getCommandHandlingAdapterInstances(
            final String tenant,
            final String deviceId,
            final List<String> viaGateways,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(viaGateways);

        return getOrCreateDeviceConnectionClient(tenant)
                .compose(client -> client.getCommandHandlingAdapterInstances(deviceId, viaGateways, context));
    }
}
