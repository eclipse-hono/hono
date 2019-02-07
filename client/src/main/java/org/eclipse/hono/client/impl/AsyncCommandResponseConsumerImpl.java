/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A Vertx-Proton based client for consuming async command response messages from a Hono server.
 */
public class AsyncCommandResponseConsumerImpl extends AbstractConsumer implements MessageConsumer {

    private static final String ASYNC_COMMAND_RESPONSE_ADDRESS_TEMPLATE = CommandConstants.COMMAND_ENDPOINT
            + "%s%s%s%s";

    private AsyncCommandResponseConsumerImpl(final Context context, final ClientConfigProperties config,
            final ProtonReceiver receiver) {
        super(context, config, receiver);
    }

    /**
     * Creates a new async command response consumer for a tenant for commands with the given {@code replyId}.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consume async command responses for.
     * @param replyId The {@code replyId} of commands to consume async responses for.
     * @param asyncCommandResponseConsumer The consumer to invoke with async command response received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String replyId,
            final BiConsumer<ProtonDelivery, Message> asyncCommandResponseConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler,
            final Handler<String> closeHook) {

        create(context, clientConfig, con, tenantId, replyId, Constants.DEFAULT_PATH_SEPARATOR, asyncCommandResponseConsumer,
                creationHandler, closeHook);
    }

    /**
     * Creates a new async command response consumer for a tenant for commands with the given {@code replyId}.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consume async command responses for.
     * @param replyId The {@code replyId} of commands to consume async responses for.
     * @param pathSeparator The address path separator character used by the server.
     * @param asyncCommandResponseConsumer The consumer to invoke with async command response received.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @param closeHook The handler to invoke when the link is closed by the peer (may be {@code null}).
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String replyId,
            final String pathSeparator,
            final BiConsumer<ProtonDelivery, Message> asyncCommandResponseConsumer,
            final Handler<AsyncResult<MessageConsumer>> creationHandler,
            final Handler<String> closeHook) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        Objects.requireNonNull(pathSeparator);
        Objects.requireNonNull(asyncCommandResponseConsumer);
        Objects.requireNonNull(creationHandler);

        createReceiver(context, clientConfig, con, String.format(ASYNC_COMMAND_RESPONSE_ADDRESS_TEMPLATE, pathSeparator,
                tenantId, pathSeparator, replyId),
                ProtonQoS.AT_LEAST_ONCE, asyncCommandResponseConsumer::accept, closeHook).setHandler(created -> {
                    if (created.succeeded()) {
                        creationHandler.handle(Future.succeededFuture(
                                new AsyncCommandResponseConsumerImpl(context, clientConfig, created.result())));
                    } else {
                        creationHandler.handle(Future.failedFuture(created.cause()));
                    }
                });
    }

}
