/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.AbstractConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around an AMQP receiver link for consuming commands.
 */
public class CommandConsumer extends AbstractConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CommandConsumer.class);

    private CommandConsumer(final Context context, final ClientConfigProperties config,
            final ProtonReceiver protonReceiver) {
        super(context, config, protonReceiver);
    }

    /**
     * Creates a new command consumer.
     * <p>
     * The underlying receiver link will be created with the following properties:
     * <ul>
     * <li><em>auto accept</em> will be set to {@code true}</li>
     * <li><em>pre-fetch size</em> will be set to {@code 0} to enforce manual flow control.
     * However, the sender will be issued one credit on link establishment.</li>
     * </ul>
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the commands should be consumed.
     * @param commandHandler The handler to invoke for each command received.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static final void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<Command> commandHandler,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);
        Objects.requireNonNull(receiverCloseHook);
        Objects.requireNonNull(creationHandler);

        LOG.trace("creating new command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);

        final String address = ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString();
        final ClientConfigProperties props = new ClientConfigProperties(clientConfig);
        props.setInitialCredits(0);

        final AtomicReference<ProtonReceiver> receiverRef = new AtomicReference<>();

        createReceiver(
                context,
                props,
                con,
                address,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, msg) -> {
                    final Command command = Command.from(receiverRef.get(), delivery, msg, tenantId, deviceId);
                    commandHandler.handle(command);
                },
                receiverCloseHook).setHandler(s -> {

                    if (s.succeeded()) {
                        final ProtonReceiver receiver = s.result();
                        LOG.debug("successfully created command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                        receiverRef.set(receiver);
                        receiver.flow(1);
                        creationHandler.handle(Future.succeededFuture(new CommandConsumer(context, props, receiver)));
                    } else {
                        LOG.debug("failed to create command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId, s.cause());
                        creationHandler.handle(Future.failedFuture(s.cause()));
                    }
                });
    }
}
