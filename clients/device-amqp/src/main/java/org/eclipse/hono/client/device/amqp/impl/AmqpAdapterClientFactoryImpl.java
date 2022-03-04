/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp.impl;

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycleWrapper;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.client.device.amqp.AmqpSenderLink;
import org.eclipse.hono.client.device.amqp.CommandResponder;
import org.eclipse.hono.client.device.amqp.EventSender;
import org.eclipse.hono.client.device.amqp.TelemetrySender;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.client.impl.ClientFactory;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;

import io.vertx.core.Future;

/**
 * A factory for creating clients for Hono's AMQP adapter that uses caching for the senders to ensure that they always
 * contain a open Vert.x ProtonSender.
 */
public final class AmqpAdapterClientFactoryImpl extends ConnectionLifecycleWrapper<HonoConnection>
        implements AmqpAdapterClientFactory {

    private final HonoConnection connection;

    private final CachingClientFactory<TelemetrySender> telemetrySenderClientFactory;
    private final CachingClientFactory<EventSender> eventSenderClientFactory;
    private final CachingClientFactory<CommandResponder> commandResponseSenderClientFactory;

    private final String tenantId;
    private final ClientFactory<CommandConsumer> commandConsumerFactory;

    /**
     * Creates a new factory instance for an existing connection and a given tenant.
     *
     * @param connection The connection to use.
     * @param tenantId The ID of the tenant to be used for the clients created by this factory.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public AmqpAdapterClientFactoryImpl(final HonoConnection connection, final String tenantId) {
        super(connection);
        this.connection = Objects.requireNonNull(connection);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.connection.addDisconnectListener(con -> onDisconnect());

        telemetrySenderClientFactory = new CachingClientFactory<>(connection.getVertx(), AmqpSenderLink::isOpen);
        eventSenderClientFactory = new CachingClientFactory<>(connection.getVertx(), AmqpSenderLink::isOpen);
        commandResponseSenderClientFactory = new CachingClientFactory<>(connection.getVertx(), AmqpSenderLink::isOpen);
        commandConsumerFactory = new ClientFactory<>();
    }

    private void onDisconnect() {
        telemetrySenderClientFactory.clearState();
        eventSenderClientFactory.clearState();
        commandResponseSenderClientFactory.clearState();
    }

    @Override
    public Future<TelemetrySender> getOrCreateTelemetrySender() {

        final String cacheKey = AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, null, null);
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    telemetrySenderClientFactory.getOrCreateClient(
                            cacheKey,
                            () -> AmqpAdapterClientTelemetrySenderImpl.create(
                                    connection, tenantId,
                                    onSenderClosed -> telemetrySenderClientFactory.removeClient(cacheKey)),
                            result);
                }));
    }

    @Override
    public Future<EventSender> getOrCreateEventSender() {

        final String cacheKey = AddressHelper.getTargetAddress(EventConstants.EVENT_ENDPOINT, tenantId, null, null);
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    eventSenderClientFactory.getOrCreateClient(
                            cacheKey,
                            () -> AmqpAdapterClientEventSenderImpl.create(
                                    connection, tenantId,
                                    onSenderClosed -> eventSenderClientFactory.removeClient(cacheKey)),
                            result);
                }));
    }

    @Override
    public Future<CommandConsumer> createDeviceSpecificCommandConsumer(final String deviceId,
            final Consumer<Message> messageHandler) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(messageHandler);

        return connection.executeOnContext(result -> commandConsumerFactory.createClient(
                () -> AmqpAdapterClientCommandConsumer.create(
                        connection,
                        tenantId,
                        deviceId,
                        (delivery, message) -> messageHandler.accept(message)),
                result));
    }

    @Override
    public Future<CommandConsumer> createCommandConsumer(final Consumer<Message> messageHandler) {

        Objects.requireNonNull(messageHandler);

        return connection.executeOnContext(result -> commandConsumerFactory.createClient(
                () -> AmqpAdapterClientCommandConsumer.create(
                        connection,
                        (delivery, message) -> messageHandler.accept(message)),
                result));
    }

    @Override
    public Future<CommandResponder> getOrCreateCommandResponseSender() {

        final String cacheKey = CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT + "/" + tenantId;
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    commandResponseSenderClientFactory.getOrCreateClient(
                            cacheKey,
                            () -> AmqpAdapterClientCommandResponseSender.create(
                                    connection, tenantId,
                                    onSenderClosed -> commandResponseSenderClientFactory.removeClient(cacheKey)),
                            result);
                }));
    }

    /**
     * Gets the default timeout used when checking whether this client factory is connected to the service.
     * <p>
     * The value returned here is the {@link org.eclipse.hono.config.ClientConfigProperties#getLinkEstablishmentTimeout()}.
     *
     * @return The timeout value in milliseconds.
     */
    private long getDefaultConnectionCheckTimeout() {
        return connection.getConfig().getLinkEstablishmentTimeout();
    }
}
