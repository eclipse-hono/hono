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

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;

import io.opentracing.Span;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for Hono's Command and Control API.
 *
 */
public class CommandClientImpl extends AbstractRequestResponseClient<BufferResult> implements CommandClient {

    private static final Logger LOG = LoggerFactory.getLogger(CommandClientImpl.class);

    private long messageCounter;

    /**
     * Creates a request-response client.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks(ProtonConnection)} or
     * {@link #createLinks(ProtonConnection, Handler, Handler)} only.
     *
     * @param context The vert.x context to run message exchanges with the peer on.
     * @param config The configuration properties to use.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to create the client for.
     * @param replyId The replyId to use in the reply-to address.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    CommandClientImpl(
            final Context context,
            final ClientConfigProperties config,
            final String tenantId,
            final String deviceId,
            final String replyId) {

        super(context, config, tenantId, deviceId, replyId);
    }

    /**
     * Creates a request-response client.
     *
     * @param context The vert.x context to run message exchanges with the peer on.
     * @param config The configuration properties to use.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to create the client for.
     * @param replyId The replyId to use in the reply-to address.
     * @param sender The link to use for sending command requests.
     * @param receiver The link to use for receiving command responses.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    CommandClientImpl(
            final Context context,
            final ClientConfigProperties config,
            final String tenantId,
            final String deviceId,
            final String replyId,
            final ProtonSender sender,
            final ProtonReceiver receiver) {

        this(context, config, tenantId, deviceId, replyId);
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
    }

    @Override
    protected String getName() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    /**
     * The command's message ID is transferred to the device in order to be able to correlate the
     * response received from the device with the request message. It is therefore
     * desirable to keep the message ID as short as possible in order to reduce the number of bytes
     * exchanged with the device.
     * <p>
     * This methods creates message IDs based on a counter that is increased on each invocation.
     *
     * @return The message ID.
     */
    @Override
    protected String createMessageId() {
        return Long.toString(messageCounter++, Character.MAX_RADIX);
    }

    @Override
    protected BufferResult getResult(final int status, final String contentType, final Buffer payload, final CacheDirective cacheDirective) {
        return BufferResult.from(status, contentType, payload);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method simply invokes {@link #sendCommand(String, String, Buffer, Map)} with
     * {@code null} as the *content-type* and {@code null} as *application properties*.
     */
    @Override
    public Future<BufferResult> sendCommand(final String command, final Buffer data) {
        return sendCommand(command, null, data, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method uses the {@linkplain #createMessageId() message ID} to correlate the response received
     * from a device with the request.
     */
    @Override
    public Future<BufferResult> sendCommand(final String command, final String contentType, final Buffer data, final Map<String, Object> properties) {

        Objects.requireNonNull(command);

        final Future<BufferResult> responseTracker = Future.future();
        createAndSendRequest(command, properties, data, contentType, responseTracker, null);

        return responseTracker.map(response -> {
            if (response.isOk()) {
                return response;
            } else {
                throw StatusCodeMapper.from(response);
            }
        });
    }

    @Override
    public Future<Void> sendOneWayCommand(final String command, final Buffer data) {
        return sendOneWayCommand(command, null, data, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendOneWayCommand(final String command, final String contentType, final Buffer data, final Map<String, Object> properties) {
        Objects.requireNonNull(command);

        final Span currentSpan = newChildSpan(null, command);

        if (sender.isOpen()) {
            final Future<BufferResult> responseTracker = Future.future();
            final Message request = ProtonHelper.message();

            AbstractHonoClient.setApplicationProperties(request, properties);

            final String messageId = createMessageId();
            request.setMessageId(messageId);
            request.setSubject(command);

            MessageHelper.setPayload(request, contentType, data);
            sendRequest(request, responseTracker.completer(), null, currentSpan);

            return responseTracker.map(ignore -> null);
        } else {
            TracingHelper.logError(currentSpan, "sender link is not open");

            return Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "sender link is not open"));
        }
    }

    /**
     * Creates a new command client for a tenant and device.
     * <p>
     * The instance created is scoped to the given device.
     * In particular, the sender link's target address is set to
     * <em>control/${tenantId}/${deviceId}</em> and the receiver link's source
     * address is set to <em>control/${tenantId}/${deviceId}/${replyId}</em>.
     * This address is also used as the value of the <em>reply-to</em>
     * property of all command request messages sent by this client.
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to create the client for.
     * @param replyId The replyId to use in the reply-to address.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
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
            final String replyId,
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
