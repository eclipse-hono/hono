/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.command;

import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.hono.client.HonoClient;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import org.eclipse.hono.client.MessageConsumer;

/**
 * A bidirectional connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public interface CommandConnection extends HonoClient {

    /**
     * Creates a receiver link in the CommandAdapter and a message handler for Commands.
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the receiver will be created.
     * @param messageConsumer The message handler for the received commands.
     * @param closeHandler The close handler for the receiver.
     * @return A future, with a MessageConsumer.
     * @throws NullPointerException if tenantId, deviceId or messageConsumer is {@code null};
     */
    Future<MessageConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            Consumer<Command> messageConsumer,
            Handler<Void> closeHandler);

    /**
     * Send back a response for a command to the business application.
     * @param commandToResponse The original command, which should be responded.
     * @param data The data to send back or {@code null}.
     * @param properties The properties to send back or {@code null}.
     * @param status The status code of the command execution.
     * @return A ProtonDelivery indicating the success
     * @throws NullPointerException if commandToResponse is {@code null};
     */
    Future<ProtonDelivery> sendCommandResponse(
            Command commandToResponse,
            Buffer data,
            Map<String, Object> properties,
            int status);

}
