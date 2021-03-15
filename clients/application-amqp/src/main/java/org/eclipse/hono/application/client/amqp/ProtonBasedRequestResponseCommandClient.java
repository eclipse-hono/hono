/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.amqp.AbstractRequestResponseServiceClient;
import org.eclipse.hono.client.amqp.RequestResponseClient;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.BufferResult;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A vertx-proton based client for sending and receiving commands synchronously.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control/">
 *      Command &amp; Control API for AMQP 1.0 Specification</a>
 */
final class ProtonBasedRequestResponseCommandClient
        extends AbstractRequestResponseServiceClient<Buffer, BufferResult> {

    private int messageCounter;

    /**
     * Creates a vertx-proton based client for sending and receiving commands synchronously.
     *
     * @param connection The connection to the service.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected ProtonBasedRequestResponseCommandClient(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory,
                new CachingClientFactory<>(connection.getVertx(), RequestResponseClient::isOpen), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getKey(final String tenantId) {
        return String.format("%s-%s", CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferResult getResult(final int status, final String contentType, final Buffer payload,
            final CacheDirective cacheDirective, final ApplicationProperties applicationProperties) {
        return BufferResult.from(status, contentType, payload, applicationProperties);
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
     * @param properties The headers to include in the command message as AMQP application properties.
     * @param context The currently active OpenTracing span context that is used to trace the execution of this
     *            operation or {@code null} if no span is currently active.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 2xx has been received from the device.
     *         If the response has no payload, the future will complete with {@code null}.
     *         <p>
     *         Otherwise, the future will fail with a {@link ServiceInvocationException} containing
     *         the (error) status code. Status codes are defined at 
     *         <a href="https://www.eclipse.org/hono/docs/api/command-and-control">Command and Control API</a>.
     * @throws NullPointerException if any of tenantId, deviceId or command are {@code null}.
     */
    public Future<BufferResult> sendCommand(final String tenantId, final String deviceId, final String command,
            final String contentType, final Buffer data, final String replyId, final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        final Span currentSpan = newChildSpan(context, "send command and receive response");

        return getOrCreateClient(tenantId, replyId)
                .compose(client -> {
                    final String messageTargetAddress = AddressHelper
                            .getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, deviceId,
                                    connection.getConfig());
                    return client.createAndSendRequest(command, messageTargetAddress, properties, data, contentType,
                            this::getRequestResponseResult, currentSpan);
                })
                .recover(error -> {
                    Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(error));
                    TracingHelper.logError(currentSpan, error);
                    return Future.failedFuture(error);
                })
                .map(bufferResult -> {
                    setTagsForResult(currentSpan, bufferResult);
                    if (bufferResult != null && bufferResult.isError()) {
                        throw StatusCodeMapper.from(bufferResult);
                    }
                    return bufferResult;
                })
                .onComplete(r -> currentSpan.finish());
    }

    private Future<RequestResponseClient<BufferResult>> getOrCreateClient(final String tenantId, final String replyId) {

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
