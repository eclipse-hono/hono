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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public class CommandConnectionImpl extends HonoClientImpl implements CommandConnection {

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    protected final Map<String, MessageConsumer> commandReceivers = new HashMap<>();

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
    public CommandConnectionImpl(final Vertx vertx, final CommandConfigProperties clientConfigProperties) {
        super(vertx, clientConfigProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void clearState() {
        super.clearState();
        commandReceivers.clear();
    }

    /**
     * {@inheritDoc}
     */
    public final Future<MessageConsumer> getOrCreateCommandConsumer(
            final String tenantId,
            final String deviceId,
            final BiConsumer<ProtonDelivery, Message> commandConsumer,
            final Handler<Void> closeHandler) {
        // TODO: see getOrCreateSender().. needed for this per device receives?
        final MessageConsumer messageConsumer = commandReceivers.get(Device.asAddress(tenantId, deviceId));
        if (messageConsumer != null) {
            return Future.succeededFuture(messageConsumer);
        } else {
            return createCommandConsumer(tenantId, deviceId, commandConsumer, closeHandler);
        }
    }

    private Future<MessageConsumer> createCommandConsumer(
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
                    messageConsumer, closeHook -> {
                        commandReceivers.remove(Device.asAddress(tenantId, deviceId));
                        closeHandler.handle(null);
                    }, creation -> {
                        if(creation.succeeded()) {
                            commandReceivers.put(Device.asAddress(tenantId, deviceId), creation.result());
                        }
                        result.complete(creation.result());
                    });
            return result;
        });
    }

    /**
     * {@inheritDoc}
     */
    public Future<Void> closeCommandConsumer(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        final MessageConsumer commandReceiverLink = commandReceivers.get(Device.asAddress(tenantId, deviceId));
        final Future<Void> future = Future.future();
        if (commandReceiverLink != null) {
            commandReceiverLink.close(closeHandler -> {
                if (closeHandler.failed()) {
                    LOG.error("Command receiver link close failed: {}", closeHandler.cause());
                    future.fail(closeHandler.cause());
                }
                else {
                    future.complete();
                }
            });
        } else {
            LOG.error("Command receiver should be closed but could not be found for tenant: [{}], device: [{}]",
                    tenantId, deviceId);
            future.fail("Command receiver should be closed but could not be found for tenant");
        }
        return future;
    }

    /**
     * {@inheritDoc}
     */
    public Future<CommandResponseSender> getOrCreateCommandResponseSender(
            final String tenantId,
            final String deviceId,
            final String replyId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(replyId);
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

    /**
     * {@inheritDoc}
     */
    public Future<Void> closeCommandResponseSender(final String tenantId, final String deviceId, final String replyId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(replyId);
        final MessageSender commandResponseSender = activeSenders.get(CommandResponseSenderImpl.getTargetAddress(tenantId, deviceId, replyId));
        final Future<Void> future = Future.future();
        if (commandResponseSender != null) {
            commandResponseSender.close(closeHandler -> {
                if (closeHandler.failed()) {
                    LOG.error("Command response sender link close failed: {}", closeHandler.cause());
                    future.fail(closeHandler.cause());
                }
                else {
                    future.complete();
                }
            });
        } else {
            LOG.error("Command response sender should be closed but could not be found for tenant: [{}], device: [{}]",
                    tenantId, deviceId);
            future.fail("Command response sender should be closed but could not be found");
        }
        return future;
    }

    /**
     * {@inheritDoc}
     */
    public Future<Void> closeCommandResponseSenders(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        final Future<Void> future = Future.future();
        final String controlAddress = ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString();
        final Set<String> keys = activeSenders.keySet();
        for(final String key : keys) {
            if (key.startsWith(controlAddress)) {
                final Future<Void> sub = Future.future();
                activeSenders.get(key).close(c->{
                    if(c.succeeded()) {
                        sub.succeeded();
                    } else {
                        sub.fail(c.cause());
                    }
                });
                future.compose(v-> sub);
            }
        }
        return future;
    }
}
