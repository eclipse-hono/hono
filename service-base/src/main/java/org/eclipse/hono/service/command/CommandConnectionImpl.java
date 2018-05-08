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

import io.vertx.core.buffer.Buffer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

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
    public Future<Void> createCommandResponder(final String tenantId, final String deviceId,
            final Handler<Command> commandHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);

        LOG.debug("create a command receiver for [tenant: {}, device-id: {}]", tenantId, deviceId);
        Future<Void> result = Future.future();
        connect().setHandler(h -> {
            if (h.succeeded()) {
                getConnection()
                        .createReceiver(ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId)
                                .toString()) // TODO
                        .openHandler(oh -> {
                            if (oh.succeeded()) {
                                LOG.debug("command receiver successfully opened for [tenant: {}, device-id: {}]",
                                        tenantId, deviceId);
                                ProtonReceiver protonReceiver = oh.result();
                                CommandResponder responder = new CommandResponder(protonReceiver);
                                protonReceiver.handler((delivery, message) -> {
                                    LOG.debug("command message received on [address: {}]", message.getAddress());
                                    commandHandler.handle(new Command(responder, message));
                                });
                                result.complete();
                            } else {
                                LOG.debug("command receiver failed opening for [tenant: {}, device-id: {}] : {}",
                                        tenantId, deviceId, oh.cause().getMessage());
                                result.fail(oh.cause());
                            }
                        }).open();
            } else {
                result.fail(h.cause());
            }
        });
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Future<Void> sendCommandResponse(final Command command, final Buffer data,
            final Map<String, Object> properties,
            final Handler<ProtonDelivery> update) {
        Objects.requireNonNull(command);
        LOG.debug("create a command responder (sender link) for [replyAddress: {}]", command.getReplyAddress());
        Future<Void> result = Future.future();
        synchronized (command) {
            ProtonSender sender = command.getResponder().getSender();
            if (sender == null || !sender.isOpen()) {
                connect().setHandler(h -> {
                    getConnection().createSender(command.getReplyAddress())
                            .openHandler(oh -> {
                                if (oh.succeeded()) {
                                    LOG.debug("command reply sender opened successful for [replyAddress: {}]",
                                            command.getReplyAddress());
                                    command.getResponder().setSender(oh.result());
                                    command.sendResponse(data, properties, update);
                                    result.complete();
                                } else {
                                    LOG.debug("command reply failed opening for [replyAddress: {}] : {}",
                                            command.getReplyAddress(), oh.cause());
                                }
                            }).open();
                });
            } else {
                LOG.debug("reuse open command reply sender for [replyAddress: {}]", command.getReplyAddress());
                command.sendResponse(data, properties, update);
                result.complete();
            }
        }
        return result;
    }

}
