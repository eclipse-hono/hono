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

package org.eclipse.hono.client.impl;

import java.math.BigInteger;
import java.util.Objects;

import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.BufferResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonConnection;

/**
 * A Vertx-Proton based client for Hono's Command and Control API.
 *
 */
public class CommandClientImpl extends AbstractRequestResponseClient<BufferResult> implements CommandClient {

    private static final Logger LOG = LoggerFactory.getLogger(CommandClientImpl.class);

    private long messageCounter;

    CommandClientImpl(final Context context, final ClientConfigProperties config, final String tenantId, final String deviceId, final String replyId) {
        super(context, config, tenantId, deviceId, replyId);
    }

    @Override
    protected String getName() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    @Override
    protected String createMessageId() {
        // Since the messages are scoped to the link between this client and the adapter it is not needed
        // to create a UUID and a counter with a compressed serialized format is sufficient.
        // This has a value, since this id is also given as part of the request id to device and should be
        // as short as possible.
        return BigInteger.valueOf(messageCounter++).toString(Character.MAX_RADIX);
    }

    @Override
    protected BufferResult getResult(final int status, final Buffer payload, final CacheDirective cacheDirective) {
        return BufferResult.from(status, payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Buffer> sendCommand(final String command, final Buffer data) {

        Objects.requireNonNull(command);

        final Future<BufferResult> responseTracker = Future.future();
        createAndSendRequest(command, null, data, null, responseTracker.completer(), null);

        return responseTracker.map(response -> {
            if (response.isOk()) {
                return response.getPayload();
            } else {
                throw StatusCodeMapper.from(response);
            }
        });
    }

    /**
     * Creates a new command client for a tenant and device.
     *
     * @param tenantId The tenant to create the client for.
     * @param deviceId The device to create the client for.
     * @param replyId The replyId used as a postfix to create the reply-to-address with.
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static final void create(
            final String tenantId,
            final String deviceId,
            final String replyId,
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook,
            final Handler<AsyncResult<CommandClient>> creationHandler) {

        final CommandClientImpl client = new CommandClientImpl(context, clientConfig, tenantId, deviceId, replyId);
        client.createLinks(con, senderCloseHook, receiverCloseHook).setHandler(s -> {
            if (s.succeeded()) {
                LOG.debug("successfully created command client for [{}]", tenantId);
                creationHandler.handle(Future.succeededFuture(client));
            } else {
                LOG.debug("failed to create command client for [{}]", tenantId, s.cause());
                creationHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

}