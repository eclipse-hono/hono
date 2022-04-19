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
import org.eclipse.hono.client.device.amqp.impl.ProtonBasedAmqpAdapterClient;

import io.vertx.core.Future;

/**
 * A Vert.x based client for interacting with Hono's AMQP adapter.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/dev/user-guide/amqp-adapter/">AMQP Adapter User Guide</a>
 */
public interface AmqpAdapterClient extends ConnectionLifecycle<HonoConnection>, TelemetrySender, EventSender, CommandResponder {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @return The factory.
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    static AmqpAdapterClient create(final HonoConnection connection) {
        return new ProtonBasedAmqpAdapterClient(connection);
    }

    /**
     * Creates a client for consuming commands from Hono's AMQP protocol adapter for an authenticated device.
     * <p>
     * In case the authenticated device is a (protocol) gateway, the consumer will receive commands targeted at
     * any of the devices that that gateway is authorized to act on behalf of.
     * <p>
     * The command passed in to the command consumer will be settled automatically.
     *
     * @param messageHandler The handler to invoke with every command received.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this factory is not connected.
     * @throws NullPointerException if message handler is {@code null}.
     */
    Future<CommandConsumer> createCommandConsumer(Consumer<Message> messageHandler);

    /**
     * Creates a client for consuming commands from Hono's AMQP protocol adapter for a specific device.
     * <p>
     * When implementing a (protocol) gateway, this can be used to receive commands for a specific device.
     * <p>
     * The command passed in to the command consumer will be settled automatically.
     *
     * @param tenantId The tenant that the device belongs to, or {@code null} to determine the tenant from 
     *                 the device that has authenticated to the AMQP adapter.
     *                 Unauthenticated clients must provide a non-{@code null} value to indicate the tenant of the
     *                 device to consume commands for.
     * @param deviceId The identifier of the device to consume commands for.
     *                 Authenticated gateway devices can use this parameter to consume commands for another device
     *                 that the gateway is authorized to act on behalf of.
     *                 Unauthenticated clients use this property to indicate the device to consume commands for.
     * @param messageHandler The handler to invoke with every command received.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this factory is not connected.
     * @throws NullPointerException if device ID or message handler are {@code null}.
     */
    Future<CommandConsumer> createDeviceSpecificCommandConsumer(
            String tenantId,
            String deviceId,
            Consumer<Message> messageHandler);
}
