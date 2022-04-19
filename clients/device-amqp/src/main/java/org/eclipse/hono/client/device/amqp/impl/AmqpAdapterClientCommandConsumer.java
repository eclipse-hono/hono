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
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.AbstractHonoClient;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around an AMQP receiver link for consuming commands from Hono's AMQP adapter. This implementation tries to
 * restore closed links by trying to create a new link each time the connection is closed.
 */
public class AmqpAdapterClientCommandConsumer extends AbstractHonoClient implements CommandConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpAdapterClientCommandConsumer.class);

    /**
     * Creates a consumer for a connection and a receiver link.
     *
     * @param connection The connection to the AMQP Messaging Network over which commands are received.
     * @param receiver The receiver link for command messages.
     * @throws NullPointerException if connection or receiver is {@code null}.
     */
    private AmqpAdapterClientCommandConsumer(final HonoConnection connection, final ProtonReceiver receiver) {
        super(connection);
        this.receiver = Objects.requireNonNull(receiver);
    }

    /**
     * Creates a new command consumer for the given device.
     * <p>
     * The underlying receiver link will be created with its <em>autoAccept</em> property set to {@code true} and with
     * the connection's default pre-fetch size.
     *
     * @param con The connection to the server.
     * @param tenantId The tenant that the device belongs to, or {@code null} to determine the tenant from 
     *                 the device that has authenticated to the AMQP adapter.
     * @param deviceId The device for which the commands should be consumed.
     * @param messageHandler The handler to invoke with every message received.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters except tenant ID are {@code null}.
     */
    public static Future<CommandConsumer> create(
            final HonoConnection con,
            final String tenantId,
            final String deviceId,
            final BiConsumer<ProtonDelivery, Message> messageHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(messageHandler);

        final ResourceIdentifier address = ResourceIdentifier
                .fromPath(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, deviceId);
        return createCommandConsumer(con, messageHandler, address);
    }

    /**
     * Creates a new command consumer.
     * <p>
     * The underlying receiver link will be created with its <em>autoAccept</em> property set to {@code true} and with
     * the connection's default pre-fetch size.
     *
     * @param con The connection to the server.
     * @param messageHandler The handler to invoke with every message received.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Future<CommandConsumer> create(
            final HonoConnection con,
            final BiConsumer<ProtonDelivery, Message> messageHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(messageHandler);

        final ResourceIdentifier address = ResourceIdentifier
                .fromPath(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT);
        return createCommandConsumer(con, messageHandler, address);
    }

    private static Future<CommandConsumer> createCommandConsumer(
            final HonoConnection con,
            final BiConsumer<ProtonDelivery, Message> messageHandler,
            final ResourceIdentifier address) {

        return con.isConnected(con.getConfig().getLinkEstablishmentTimeout())
                .compose(v -> createReceiver(con, messageHandler, address))
                .map(rec -> {
                    final AmqpAdapterClientCommandConsumer consumer = new AmqpAdapterClientCommandConsumer(con, rec);
                    con.addReconnectListener(
                            c -> createReceiver(con, messageHandler, address).onSuccess(consumer::setReceiver));
                    return consumer;
                });

    }

    private static Future<ProtonReceiver> createReceiver(final HonoConnection con,
            final BiConsumer<ProtonDelivery, Message> messageHandler, final ResourceIdentifier address) {
        return con.createReceiver(
                address.toString(),
                ProtonQoS.AT_LEAST_ONCE,
                messageHandler::accept,
                // TODO maybe this could be handled by reopening the link?
                remote -> LOG.info("The remote [{}] closed the receiver link", remote));
    }

    private void setReceiver(final ProtonReceiver protonReceiver) {
        receiver = protonReceiver;
    }

    // visible for testing
    ProtonReceiver getReceiver() {
        return receiver;
    }

    @Override
    public final Future<Void> close(final SpanContext spanContext) {
        return closeLinks();
    }
}
