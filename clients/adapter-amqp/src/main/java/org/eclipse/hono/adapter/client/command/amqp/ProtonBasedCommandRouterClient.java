/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.client.command.amqp;

import java.time.Duration;
import java.util.Objects;

import org.eclipse.hono.adapter.client.amqp.AbstractServiceClient;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.CommandRouterConstants;
import org.eclipse.hono.util.Constants;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;

/**
 * A vertx-proton based client of Hono's Command Router service.
 *
 */
public class ProtonBasedCommandRouterClient extends AbstractServiceClient implements CommandRouterClient {

    private final CachingClientFactory<ProtonBasedTenantCommandRouterClient> clientFactory;

    /**
     * Creates a new client for a connection.
     *
     * @param connection The connection to the Command Router service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommandRouterClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final ProtocolAdapterProperties adapterConfig) {
        super(connection, samplerFactory, adapterConfig);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    private Future<ProtonBasedTenantCommandRouterClient> getOrCreateCommandRouterClient(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection
                .isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            ProtonBasedTenantCommandRouterClient.getTargetAddress(tenantId),
                            () -> ProtonBasedTenantCommandRouterClient.create(
                                    connection,
                                    tenantId,
                                    samplerFactory.create(CommandRouterConstants.COMMAND_ROUTER_ENDPOINT),
                                    this::removeCommandRouterClient,
                                    this::removeCommandRouterClient),
                            result);
                }));
    }

    private void removeCommandRouterClient(final String tenantId) {
        clientFactory.removeClient(ProtonBasedTenantCommandRouterClient.getTargetAddress(tenantId));
    }


    private void handleTenantTimeout(final Message<String> msg) {
        final String tenantId = msg.body();
        final String address = ProtonBasedTenantCommandRouterClient.getTargetAddress(tenantId);
        final ProtonBasedTenantCommandRouterClient client = clientFactory.getClient(address);
        if (client != null) {
            client.close(v -> clientFactory.removeClient(address));
        }
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

    @Override
    public Future<Void> setLastKnownGatewayForDevice(final String tenantId, final String deviceId, final String gatewayId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);

        return getOrCreateCommandRouterClient(tenantId)
                .compose(client -> client.setLastKnownGatewayForDevice(deviceId, gatewayId, context));
    }

    @Override
    public Future<Void> registerCommandConsumer(final String tenantId, final String deviceId, final String adapterInstanceId,
            final Duration lifespan, final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        return getOrCreateCommandRouterClient(tenantId)
                .compose(client -> client.registerCommandConsumer(deviceId, adapterInstanceId, lifespan, context));
    }

    @Override
    public Future<Void> unregisterCommandConsumer(final String tenantId, final String deviceId, final String adapterInstanceId,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(adapterInstanceId);

        return getOrCreateCommandRouterClient(tenantId)
                .compose(client -> client.unregisterCommandConsumer(deviceId, adapterInstanceId, context));
    }

}
