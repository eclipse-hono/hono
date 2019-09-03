/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.util.Constants;

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
     */
    public DownstreamSenderFactoryImpl(final HonoConnection connection) {
        super(connection);
        clientFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
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
        return connection.executeOrRunOnContext(result -> {
            clientFactory.getOrCreateClient(
                    TelemetrySenderImpl.getTargetAddress(tenantId, null),
                    () -> TelemetrySenderImpl.create(connection, tenantId,
                            onSenderClosed -> {
                                clientFactory.removeClient(TelemetrySenderImpl.getTargetAddress(tenantId, null));
                            }),
                    result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<DownstreamSender> getOrCreateEventSender(final String tenantId) {

        Objects.requireNonNull(tenantId);
        return connection.executeOrRunOnContext(result -> {
            clientFactory.getOrCreateClient(
                    EventSenderImpl.getTargetAddress(tenantId, null),
                    () -> EventSenderImpl.create(connection, tenantId,
                            onSenderClosed -> {
                                clientFactory.removeClient(EventSenderImpl.getTargetAddress(tenantId, null));
                            }),
                    result);
        });
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String telemetryAddress = TelemetrySenderImpl.getTargetAddress(msg.body(), null);
        final DownstreamSender telemetryClient = clientFactory.getClient(telemetryAddress);
        if (telemetryClient != null) {
            telemetryClient.close(v -> clientFactory.removeClient(telemetryAddress));
        }

        final String eventAddress = EventSenderImpl.getTargetAddress(msg.body(), null);
        final DownstreamSender eventClient = clientFactory.getClient(eventAddress);
        if (eventClient != null) {
            eventClient.close(v -> clientFactory.removeClient(eventAddress));
        }
    }
}
