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

package org.eclipse.hono.client.device.amqp;

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.device.amqp.impl.AmqpAdapterClientFactoryImpl;

import io.vertx.core.Future;

/**
 * A Vert.x based factory for creating tenant scoped clients for Hono's AMQP adapter.
 */
public interface AmqpAdapterClientFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param tenantId The ID of the tenant for which the connection is authenticated.
     * @return The factory.
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    static AmqpAdapterClientFactory create(final HonoConnection connection, final String tenantId) {
        return new AmqpAdapterClientFactoryImpl(connection, tenantId);
    }

    /**
     * Gets a client for sending telemetry data to Hono's AMQP protocol adapter.
     * <p>
     * The client returned may be either newly created or it may be an existing client for the tenant that this factory
     * instance belongs to.
     * <p>
     * <b>Do not hold a reference to the returned sender.</b> For each send operation retrieve the sender from the
     * factory to ensure that it contains a valid and open AMQP link.
     *
     * @return A future that will complete with the sender once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     *
     * @see "https://www.eclipse.org/hono/docs/dev/user-guide/amqp-adapter/"
     */
    Future<TelemetrySender> getOrCreateTelemetrySender();

    /**
     * Gets a client for sending events to Hono's AMQP protocol adapter.
     * <p>
     * The client returned may be either newly created or it may be an existing client for the tenant that this factory
     * instance belongs to.
     * <p>
     * <b>Do not hold a reference to the returned sender.</b> For each send operation retrieve the sender from the
     * factory to ensure that it contains a valid and open AMQP link.
     *
     * @return A future that will complete with the sender once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     *
     * @see "https://www.eclipse.org/hono/docs/dev/user-guide/amqp-adapter/"
     */
    Future<EventSender> getOrCreateEventSender();

    /**
     * Creates a client for consuming commands from Hono's AMQP protocol adapter for a specific device.
     * <p>
     * When implementing a (protocol) gateway, this can be used to receive commands for a specific device.
     * <p>
     * The command passed in to the command consumer will be settled automatically.
     *
     * @param deviceId The device to consume commands for.
     * @param messageHandler The handler to invoke with every command received.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this factory is not connected.
     * @throws NullPointerException if any of the parameters is {@code null}.
     *
     * @see "https://www.eclipse.org/hono/docs/dev/user-guide/amqp-adapter/"
     */
    Future<CommandConsumer> createDeviceSpecificCommandConsumer(String deviceId, Consumer<Message> messageHandler);

    /**
     * Creates a client for consuming commands from Hono's AMQP protocol adapter for an authenticated device or on a
     * (protocol) gateway for all devices on whose behalf it acts.
     * <p>
     * The command passed in to the command consumer will be settled automatically.
     *
     * @param messageHandler The handler to invoke with every command received.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this factory is not connected.
     * @throws NullPointerException if any of the message handler is {@code null}.
     *
     * @see "https://www.eclipse.org/hono/docs/dev/user-guide/amqp-adapter/"
     */
    Future<CommandConsumer> createCommandConsumer(Consumer<Message> messageHandler);

    /**
     * Gets a client for sending command responses to Hono's AMQP protocol adapter.
     * <p>
     * The client returned may be either newly created or it may be an existing client for the tenant that this factory
     * instance belongs to.
     * <p>
     * <b>Do not hold a reference to the returned sender.</b> For each send operation retrieve the sender from the
     * factory to ensure that it contains a valid and open AMQP link.
     *
     * @return A future that will complete with the sender once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     *
     * @see "https://www.eclipse.org/hono/docs/dev/user-guide/amqp-adapter/"
     */
    Future<CommandResponder> getOrCreateCommandResponseSender();
}
