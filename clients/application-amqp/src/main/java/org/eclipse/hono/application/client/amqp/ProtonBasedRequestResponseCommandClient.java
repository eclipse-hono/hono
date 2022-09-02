/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.application.client.amqp;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.RequestResponseResult;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A vertx-proton based client for sending and receiving commands synchronously.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control/">
 *      Command &amp; Control API for AMQP 1.0 Specification</a>
 */
public final class ProtonBasedRequestResponseCommandClient extends
        AbstractRequestResponseServiceClient<DownstreamMessage<AmqpMessageContext>, RequestResponseResult<DownstreamMessage<AmqpMessageContext>>> {

    /**
     * The default number of milliseconds to wait for a disposition for a command message.
     */
    public static final long DEFAULT_COMMAND_TIMEOUT_IN_MS = 10000;

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtonBasedRequestResponseCommandClient.class);
    private int messageCounter;

    /**
     * Creates a vertx-proton based client for sending and receiving commands synchronously.
     *
     * @param connection The connection to the service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    ProtonBasedRequestResponseCommandClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen), null);
    }

    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId);
    }

    /**
     * Sends a command to a device and expects a response.
     * <p>
     * A device needs to be (successfully) registered before a client can upload
     * any data for it. The device also needs to be connected to a protocol adapter
     * and needs to have indicated its intent to receive commands.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to send the command to.
     * @param command The name of the command.
     * @param contentType The type of the data submitted as part of the command or {@code null} if unknown.
     * @param data The input data to the command or {@code null} if the command has no input data.
     * @param replyId An arbitrary string which gets used for the response link address in the form of
     *            <em>command_response/${tenantId}/${replyId}</em>. If it is {@code null} then an unique 
     *                identifier generated using {@link UUID#randomUUID()} is used.
     * @param timeout The duration after which the send command request times out. If the timeout is {@code null}
     *                then the default timeout value of {@value #DEFAULT_COMMAND_TIMEOUT_IN_MS} ms is used.
     *                If the timeout duration is set to 0 then the send command request never times out.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device.
     *         If the response has no payload, the future will complete with a DownstreamMessage that has a {@code null} payload.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at 
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     * @throws IllegalArgumentException if the timeout duration value is &lt; 0
     */
    public Future<DownstreamMessage<AmqpMessageContext>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final String replyId,
            final Duration timeout,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        final long timeoutInMs = Optional.ofNullable(timeout)
                .map(t -> {
                    if (t.isNegative()) {
                        throw new IllegalArgumentException("command timeout duration must be >= 0");
                    }
                    return t.toMillis();
                })
                .orElse(DEFAULT_COMMAND_TIMEOUT_IN_MS);

        final Span currentSpan = newChildSpan(context, "send command and receive response");

        return getOrCreateClient(tenantId, replyId)
                .onSuccess(client -> client.setRequestTimeout(timeoutInMs))
                .compose(client -> {
                    final String messageTargetAddress = ResourceIdentifier.from(
                                CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT,
                                tenantId,
                                deviceId)
                            .toString();
                    return client.createAndSendRequest(command, messageTargetAddress, null, data, contentType,
                            this::mapCommandResponse, currentSpan);
                })
                .recover(error -> {
                    Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(error));
                    TracingHelper.logError(currentSpan, error);
                    return Future.failedFuture(error);
                })
                .compose(result -> {
                    if (result == null) {
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
                    } else {
                        final DownstreamMessage<AmqpMessageContext> commandResponseMessage = result.getPayload();
                        setTagsForResult(currentSpan, result);
                        if (result.isError()) {
                            final String detailMessage = commandResponseMessage.getPayload() != null
                                    && commandResponseMessage.getPayload().length() > 0
                                    ? commandResponseMessage.getPayload().toString(StandardCharsets.UTF_8)
                                    : null;
                            return Future.failedFuture(StatusCodeMapper.from(result.getStatus(), detailMessage));
                        }
                        return Future.succeededFuture(commandResponseMessage);
                    }
                })
                .onComplete(r -> currentSpan.finish());
    }

    private RequestResponseResult<DownstreamMessage<AmqpMessageContext>> mapCommandResponse(
            final Message message,
            final ProtonDelivery delivery) {

        final DownstreamMessage<AmqpMessageContext> downStreamMessage = ProtonBasedDownstreamMessage.from(message, delivery);

        return Optional.ofNullable(AmqpUtils.getStatus(message))
                .map(status -> new RequestResponseResult<>(status, downStreamMessage,
                        CacheDirective.from(AmqpUtils.getCacheDirective(message)), null))
                .orElseGet(() -> {
                    LOGGER.warn(
                            "response message has no status code application property [reply-to: {}, correlation ID: {}]",
                            message.getReplyTo(), message.getCorrelationId());
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method has been overridden as it is defined as abstract in the parent class and not to be used.
     *
     * @throws UnsupportedOperationException if this method is invoked.
     */
    @Override
    protected RequestResponseResult<DownstreamMessage<AmqpMessageContext>> getResult(
            final int status,
            final String contentType,
            final Buffer payload,
            final CacheDirective cacheDirective,
            final ApplicationProperties applicationProperties) {

        throw new UnsupportedOperationException();
    }

    private Future<RequestResponseClient<RequestResponseResult<DownstreamMessage<AmqpMessageContext>>>> getOrCreateClient(
            final String tenantId, final String replyId) {

        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> clientFactory.getOrCreateClient(
                        getKey(tenantId),
                        () -> RequestResponseClient.forEndpoint(
                                connection,
                                CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT,
                                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                                tenantId,
                                Optional.ofNullable(replyId).orElse(UUID.randomUUID().toString()),
                                this::createMessageId,
                                samplerFactory.create(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT),
                                this::removeClient, this::removeClient),
                        result)));
    }

    /**
     * The command's message ID is transferred to the device in order to be able to correlate the response received from
     * the device with the request message. It is therefore desirable to keep the message ID as short as possible in
     * order to reduce the number of bytes exchanged with the device.
     * <p>
     * This methods creates message IDs based on a counter that is increased on each invocation.
     *
     * @return The message ID.
     */
    private String createMessageId() {
        return Long.toString(++messageCounter, Character.MAX_RADIX);
    }

}
