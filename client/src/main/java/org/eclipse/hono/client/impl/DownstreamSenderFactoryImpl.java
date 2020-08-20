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


package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;


/**
 * A factory for creating downstream senders.
 *
 */
public class DownstreamSenderFactoryImpl extends AbstractHonoClientFactory implements DownstreamSenderFactory {

    private final CachingClientFactory<DownstreamSender> clientFactory;

    /**
     * @param connection The connection to use.
     * @param samplerFactory The factory to create samplers.
     */
    public DownstreamSenderFactoryImpl(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory);
        this.clientFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDisconnect() {
        clientFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<DownstreamSender> getOrCreateTelemetrySender(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, null, connection.getConfig()),
                            () -> TelemetrySenderImpl.create(connection, tenantId, samplerFactory.create(TelemetryConstants.TELEMETRY_ENDPOINT),
                                    onSenderClosed -> {
                                        clientFactory.removeClient(AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, null, connection.getConfig()));
                                    }),
                            result);
                }));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<DownstreamSender> getOrCreateEventSender(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    clientFactory.getOrCreateClient(
                            AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, tenantId, null, connection.getConfig()),
                            () -> EventSenderImpl.create(connection, tenantId, samplerFactory.create(EventConstants.EVENT_ENDPOINT),
                                    onSenderClosed -> {
                                        clientFactory.removeClient(AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, tenantId, null, connection.getConfig()));
                                    }),
                            result);
                }));
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String telemetryAddress = AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, msg.body(), null, connection.getConfig());
        final DownstreamSender telemetryClient = clientFactory.getClient(telemetryAddress);
        if (telemetryClient != null) {
            telemetryClient.close(v -> clientFactory.removeClient(telemetryAddress));
        }

        final String eventAddress = AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, msg.body(), null, connection.getConfig());
        final DownstreamSender eventClient = clientFactory.getClient(eventAddress);
        if (eventClient != null) {
            eventClient.close(v -> clientFactory.removeClient(eventAddress));
        }
    }
}
