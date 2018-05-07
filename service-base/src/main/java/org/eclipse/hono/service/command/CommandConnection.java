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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import org.eclipse.hono.client.HonoClient;

import java.util.Map;

/**
 * A connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public interface CommandConnection extends HonoClient {

    /**
     * Creates a receiver link and a command handler for Command and Control API messages.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which this receiver will be created.
     * @param commandHandler The message handler for the received commands.
     * @return A future indicating the outcome. The future will succeed if the receiver link has been created.
     * @throws NullPointerException if tenantId, deviceId or commandHandler is {@code null};
     */
    Future<Void> createCommandResponder(String tenantId, String deviceId,
                                               Handler<Command> commandHandler);

    /**
     * Sends a result back to the sender, based on a received command message.
     *
     * @param command The received Command.
     * @param data The data so send back to the command sender or {@code null}.
     * @param properties Any application properties or {@code null}.
     * @param update The proton delivery handler or {@code null}.
     * @return A future indicating the outcome. The future will succeed if the sender link has been created.
     * @throws NullPointerException if command is {@code null};
     */
    Future<Void> sendCommandResponse(Command command, Buffer data, Map<String, Object> properties,
                                     Handler<ProtonDelivery> update);
}
