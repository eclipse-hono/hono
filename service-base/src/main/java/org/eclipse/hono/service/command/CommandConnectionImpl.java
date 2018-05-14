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
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * Implements a connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public class CommandConnectionImpl extends HonoClientImpl implements CommandConnection {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public CommandConnectionImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        super(vertx, clientConfigProperties);
    }

    /**
     * {@inheritDoc}
     */
    public final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Consumer<Command> messageConsumer,
            final Handler<Void> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(messageConsumer);
        return checkConnected().compose(con -> {
            final Future<CommandAdapter> result = Future.future();
            CommandAdapter.create(context, clientConfigProperties, connection, tenantId, deviceId,
                    (delivery, message) -> {
                        messageConsumer.accept(new Command(result.result(), message));
                    }, closeHook -> {
                        result.result().close(c->{});
                        closeHandler.handle(null);
                    }, result.completer());
            return result.succeeded() ? Future.succeededFuture(result.result()) : Future.failedFuture(result.cause());
        });
    }

    /**
     * {@inheritDoc}
     */
    public final Future<ProtonDelivery> sendCommandResponse(
            final Command commandToResponse,
            final Buffer data,
            final Map<String, Object> properties,
            final int status) {

        Objects.requireNonNull(commandToResponse);
        return checkConnected().compose(connected -> commandToResponse.getCommandAdapter().sendResponse(connection,
                commandToResponse.getReplyTo(), commandToResponse.createResponseMessage(data, properties, status)));
    }

}
