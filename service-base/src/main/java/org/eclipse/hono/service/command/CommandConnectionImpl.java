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

import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
            final BiConsumer<ProtonDelivery, Message> commandConsumer,
            final Handler<Void> closeHandler) {

        return createConsumer(
                tenantId,
                () -> newCommandConsumer(tenantId, deviceId, commandConsumer, closeHandler));
    }

    private Future<MessageConsumer> newCommandConsumer(
            final String tenantId,
            final String deviceId,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return checkConnected().compose(con -> {
            final Future<MessageConsumer> result = Future.future();
            CommandConsumer.create(context, clientConfigProperties, connection, tenantId, deviceId,
                    messageConsumer, closeHook -> closeHandler.handle(null), result.completer());
            return result;
        });
    }

    /**
     * {@inheritDoc}
     */
    public Future<CommandResponseSender> getOrCreateCommandResponseSender(
            final String tenantId,
            final String deviceId,
            final String replyId) {

        Objects.requireNonNull(tenantId);
        final Future<CommandResponseSender> result = Future.future();
        getOrCreateSender(
                CommandResponseSenderImpl.getTargetAddress(tenantId, deviceId, replyId),
                () -> createCommandResponseSender(tenantId, deviceId, replyId)).setHandler(h->{
                    if(h.succeeded()) {
                        result.complete((CommandResponseSender) h.result());
                    }
                    else {
                        result.fail(h.cause());
                    }
        });
        return result;
    }

    private Future<MessageSender> createCommandResponseSender(
            final String tenantId,
            final String deviceId,
            final String replyId) {

        return checkConnected().compose(connected -> {
            final Future<MessageSender> result = Future.future();
            CommandResponseSenderImpl.create(context, clientConfigProperties, connection, tenantId, deviceId, replyId,
                    onSenderClosed -> {
                        activeSenders.remove(CommandResponseSenderImpl.getTargetAddress(tenantId, deviceId, replyId));
                    },
                    result.completer());
            return result;
        });
    }

}
