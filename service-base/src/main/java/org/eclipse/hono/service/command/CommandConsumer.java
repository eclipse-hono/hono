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
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.AbstractConsumer;
import org.eclipse.hono.client.impl.AbstractHonoClient;
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
import io.vertx.proton.ProtonDelivery;
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
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the commands should be consumed.
     * @param messageConsumer The consumer to invoke with each command received.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static final void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<MessageConsumer>> creationHandler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(messageConsumer);
        Objects.requireNonNull(receiverCloseHook);
        Objects.requireNonNull(creationHandler);

        LOG.debug("creating new command consumer for [{}, {}]", tenantId, deviceId);

        final String address = ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString();
        AbstractHonoClient
                .createReceiver(context, clientConfig, con, address, ProtonQoS.AT_LEAST_ONCE, messageConsumer::accept,
                        receiverCloseHook)
                .setHandler(s -> {
                    if (s.succeeded()) {
                        LOG.debug("successfully created command consumer for [{}, {}]", tenantId, deviceId);
                        creationHandler
                                .handle(Future.succeededFuture(new CommandConsumer(context, clientConfig, s.result())));
                    } else {
                        LOG.debug("failed to create command consumer for [{}, {}]", tenantId, deviceId, s.cause());
                        creationHandler.handle(Future.failedFuture(s.cause()));
                    }
                });
    }

}
