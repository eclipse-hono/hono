/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonHelper;
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
     * Creates a client for sending commands to devices.
     * <p>
     * The client will be ready to use after invoking {@link #createLinks()} or
     * {@link #createLinks(Handler, Handler)} only.
     *
     * @param connection The connection to Hono.
     * @param tenantId The tenant that the device belongs to.
     * @param replyId The replyId to use in the reply-to address.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    CommandClientImpl(
            final HonoConnection connection,
            final String tenantId,
            final String replyId) {

        super(connection, tenantId, replyId);
    }

    /**
     * Creates a client for sending commands to devices.
     *
     * @param connection The connection to Hono.
     * @param tenantId The tenant that the device belongs to.
     * @param replyId The replyId to use in the reply-to address.
     * @param sender The link to use for sending command requests.
     * @param receiver The link to use for receiving command responses.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    CommandClientImpl(
            final HonoConnection connection,
            final String tenantId,
            final String replyId,
            final ProtonSender sender,
            final ProtonReceiver receiver) {

        this(connection, tenantId, replyId);
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
    }

    @Override
    protected String getName() {
        return CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT;
    }

    @Override
    protected String getReplyToEndpointName() {
        return CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT;
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
    protected BufferResult getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {
        return BufferResult.from(status, contentType, payload, applicationProperties);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method simply invokes {@link #sendCommand(String, String, String, Buffer, Map)} with
     * {@code null} as the *content-type* and {@code null} as *application properties*.
     */
    @Override
    public Future<BufferResult> sendCommand(final String deviceId, final String command, final Buffer data) {
        return sendCommand(deviceId, command, null, data, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method uses the {@linkplain #createMessageId() message ID} to correlate the response received
     * from a device with the request.
     */
    @Override
    public Future<BufferResult> sendCommand(final String deviceId, final String command, final String contentType,
            final Buffer data, final Map<String, Object> properties) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        final Span currentSpan = newChildSpan(null, command);
        TracingHelper.setDeviceTags(currentSpan, getTenantId(), deviceId);

        final Promise<BufferResult> resultTracker = Promise.promise();

        final String messageTargetAddress = AddressHelper.getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, getTenantId(), deviceId, connection.getConfig());
        createAndSendRequest(command, messageTargetAddress, properties, data, contentType, resultTracker,
                null, currentSpan);

        return mapResultAndFinishSpan(
                resultTracker.future(),
                result -> {
                    if (result.isOk()) {
                        return result;
                    } else {
                        throw StatusCodeMapper.from(result);
                    }
                },
                currentSpan);
    }

    @Override
    public Future<Void> sendOneWayCommand(final String deviceId, final String command, final Buffer data) {
        return sendOneWayCommand(deviceId, command, null, data, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendOneWayCommand(
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final Map<String, Object> properties) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        final Span currentSpan = newChildSpan(null, command);
        TracingHelper.setDeviceTags(currentSpan, getTenantId(), deviceId);

        if (sender.isOpen()) {
            final Promise<BufferResult> responseTracker = Promise.promise();
            final Message request = ProtonHelper.message();

            AbstractHonoClient.setApplicationProperties(request, properties);

            final String messageId = createMessageId();
            request.setAddress(AddressHelper.getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, getTenantId(), deviceId, connection.getConfig()));
            request.setMessageId(messageId);
            request.setSubject(command);

            MessageHelper.setPayload(request, contentType, data);
            sendRequest(request, responseTracker, null, currentSpan);

            return responseTracker.future()
                    .recover(t -> {
                        Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
                        TracingHelper.logError(currentSpan, t);
                        currentSpan.finish();
                        return Future.failedFuture(t);
                    }).map(ignore -> {
                        Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
                        currentSpan.finish();
                        return null;
                    });
        } else {
            Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_UNAVAILABLE);
            TracingHelper.logError(currentSpan, "sender link is not open");
            currentSpan.finish();
            return Future.failedFuture(new ServerErrorException(
                    HttpURLConnection.HTTP_UNAVAILABLE, "sender link is not open"));
        }
    }

    /**
     * Creates a new command client for a tenant and device.
     * <p>
     * The instance created is scoped to the given device. In particular, the target address of messages is set to
     * <em>command/${tenantId}/${deviceId}</em>, whereas the sender link's target address is set to
     * <em>command/${tenantId}</em>. The receiver link's source address is set to
     * <em>command_response/${tenantId}/${deviceId}/${replyId}</em>.
     *
     * This address is also used as the value of the <em>reply-to</em>
     * property of all command request messages sent by this client.
     *
     * @param con The connection to Hono.
     * @param tenantId The tenant that the device belongs to.
     * @param replyId The replyId to use in the reply-to address.
     * @param senderCloseHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @param receiverCloseHook A handler to invoke if the peer closes the receiver link unexpectedly.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static final Future<CommandClient> create(
            final HonoConnection con,
            final String tenantId,
            final String replyId,
            final Handler<String> senderCloseHook,
            final Handler<String> receiverCloseHook) {

        final CommandClientImpl client = new CommandClientImpl(con, tenantId, replyId);
        return client.createLinks(senderCloseHook, receiverCloseHook)
                .map(ok -> {
                    LOG.debug("successfully created command client for [{}]", tenantId);
                    return (CommandClient) client;
                }).recover(t -> {
                    LOG.debug("failed to create command client for [{}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

}
