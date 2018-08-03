/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.command;

import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A bidirectional connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public interface CommandConnection extends HonoClient {

    /**
     * Gets a command consumer for a device.
     * <p>
     * Implementations may choose to cache consumers for devices and
     * return a cached instance instead of creating a new consumer on
     * each instantiation.
     * 
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param commandConsumer The handler to invoke with every command received.
     * @param closeHandler The handler invoked when the peer detaches the link.
     * @return A future that will complete with the consumer once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if tenantId, deviceId or messageConsumer is {@code null}.
     */
    Future<MessageConsumer> getOrCreateCommandConsumer(String tenantId, String deviceId,
            BiConsumer<ProtonDelivery, Message> commandConsumer,
            Handler<Void> closeHandler);

    /**
     * Closes the command consumer for a given device.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenantId or deviceId are {@code null}.
     */
    Future<Void> closeCommandConsumer(String tenantId, String deviceId);

    /**
     * Gets a sender for sending command responses to a business application.
     * 
     * @param tenantId The ID of the tenant to send the command responses for.
     * @param replyId The ID used to build the reply address as {@code control/tenantId/replyId}.
     * @return A future that will complete with the sender once the link has been established. The future will fail if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<CommandResponseSender> getOrCreateCommandResponseSender(String tenantId, String replyId);

    /**
     * Closes the command response sender for a device.
     *
     * @param tenantId The ID of the tenant to send the command responses for.
     * @param replyId The identifier from the command's reply-to address
     *                ({@code control/${tenantId}/${replyId}}) which represents
     *                the device.
     * @return A future indicating the result of the closing operation.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<Void> closeCommandResponseSender(String tenantId, String replyId);
}
