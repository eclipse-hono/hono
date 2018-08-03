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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.service.auth.device.Device;
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

    private final Logger LOG = LoggerFactory.getLogger(getClass());

    /**
     * The consumers that can be used to receive command messages.
     * The device, which belongs to a tenant is used as the key, e.g. <em>DEFAULT_TENANT/4711</em>.
     */
    private final Map<String, MessageConsumer> commandReceivers = new HashMap<>();

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
        final MessageConsumer messageConsumer = commandReceivers.get(Device.asAddress(tenantId, deviceId));
        if (messageConsumer != null) {
            final Future<MessageConsumer> result = Future.future();
            result.complete(messageConsumer);
            return result;
        } else {
            return createConsumer(
                    tenantId,
                    () -> newCommandConsumer(tenantId, deviceId, commandConsumer, closeHandler));
        }
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
                        closeCommandConsumer(tenantId, deviceId);
                    }, creation -> {
                        if (creation.succeeded()) {
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

        final Future<Void> result = Future.future();
        final String deviceAddress = Device.asAddress(tenantId, deviceId);
        final MessageConsumer commandReceiverLink = commandReceivers.remove(deviceAddress);

        if (commandReceiverLink == null) {
            result.fail(new IllegalStateException("no command consumer found for device"));
        } else {
            commandReceiverLink.close(result);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Future<CommandResponseSender> getOrCreateCommandResponseSender(
            final String tenantId,
            final String replyId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        final Future<CommandResponseSender> result = Future.future();
        getOrCreateSender(
                CommandResponseSenderImpl.getTargetAddress(tenantId, replyId),
                () -> createCommandResponseSender(tenantId, replyId)).setHandler(h->{
            if (h.succeeded()) {
                result.complete((CommandResponseSender) h.result());
            } else {
                result.fail(h.cause());
            }
        });
        return result;
    }

    private Future<MessageSender> createCommandResponseSender(
            final String tenantId,
            final String replyId) {

        return checkConnected().compose(connected -> {
            final Future<MessageSender> result = Future.future();
            CommandResponseSenderImpl.create(context, clientConfigProperties, connection, tenantId, replyId,
                    onSenderClosed -> {
                        activeSenders.remove(CommandResponseSenderImpl.getTargetAddress(tenantId, replyId));
                    },
                    result.completer());
            return result;
        });
    }

    /**
     * {@inheritDoc}
     */
    public Future<Void> closeCommandResponseSender(final String tenantId, final String replyId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        final MessageSender commandResponseSender = activeSenders.get(CommandResponseSenderImpl.getTargetAddress(tenantId, replyId));
        final Future<Void> future = Future.future();
        if (commandResponseSender != null) {
            commandResponseSender.close(closeHandler -> {
                if (closeHandler.failed()) {
                    LOG.error("Command response sender link close failed: {}", closeHandler.cause());
                    future.fail(closeHandler.cause());
                } else {
                    future.complete();
                }
            });
        } else {
            LOG.error("Command response sender should be closed but could not be found for tenant: [{}], replyId: [{}]",
                    tenantId, replyId);
            future.fail("Command response sender should be closed but could not be found");
        }
        return future;
    }
}
