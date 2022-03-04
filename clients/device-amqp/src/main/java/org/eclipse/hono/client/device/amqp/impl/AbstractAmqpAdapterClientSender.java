/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp.impl;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.DownstreamAmqpMessageFactory;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpSenderLink;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Base class for a Vertx-Proton based client for publishing messages to Hono's AMQP adapter.
 */
public abstract class AbstractAmqpAdapterClientSender implements AmqpSenderLink {

    protected final String tenantId;

    private final HonoConnection connection;
    private final GenericSenderLink senderLink;

    /**
     * Creates a new sender.
     *
     * @param connection The connection to the server.
     * @param senderLink The link to send messages on.
     * @param tenantId The tenant to send messages for.
     */
    protected AbstractAmqpAdapterClientSender(final HonoConnection connection, final GenericSenderLink senderLink,
            final String tenantId) {
        this.connection = connection;
        this.senderLink = senderLink;
        this.tenantId = tenantId;
    }

    /**
     * Creates a span for tracking the sending of a message.
     *
     * @param deviceId The device the message will be sent to.
     * @param operationName The operation name to set in the span.
     * @param context The span context (may be null).
     * @return The created span.
     * @throws NullPointerException if deviceId or operationName is {@code null}.
     */
    protected final Span createSpan(final String deviceId, final String operationName, final SpanContext context) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(operationName);
        final Span span = TracingHelper
                .buildChildSpan(connection.getTracer(), context, operationName, getClass().getSimpleName())
                .ignoreActiveSpan()
                .withTag(Tags.PEER_HOSTNAME.getKey(), connection.getConfig().getHost())
                .withTag(Tags.PEER_PORT.getKey(), connection.getConfig().getPort())
                .withTag(TracingHelper.TAG_PEER_CONTAINER.getKey(), connection.getRemoteContainerId())
                .start();
        TracingHelper.setDeviceTags(span, tenantId, deviceId);
        return span;
    }

    /**
     * Creates the AMQP message to be sent to the AMQP adapter.
     *
     * @param deviceId The device identifier.
     * @param payload The data to send.
     *            <p>
     *            The payload will be contained in the message as an AMQP 1.0 <em>Data</em> section.
     * @param contentType The content type of the payload (may be {@code null}).
     *            <p>
     *            This parameter will be used as the value for the message's <em>content-type</em> property.
     * @param properties Optional application properties (may be {@code null}).
     *            <p>
     *            AMQP application properties that can be used for carrying data in the message other than the payload.
     * @param targetAddress The address to send the message to.
     * @return The message.
     */
    protected final Message createMessage(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, Object> properties, final String targetAddress) {
        final Message msg = ProtonHelper.message();
        msg.setAddress(targetAddress);
        DownstreamAmqpMessageFactory.addDefaults(msg, properties);
        MessageHelper.setCreationTime(msg);
        MessageHelper.setPayload(msg, contentType, payload);
        MessageHelper.addTenantId(msg, tenantId);
        MessageHelper.addDeviceId(msg, deviceId);
        return msg;
    }

    /**
     * Sends a message to the AMQP adapter.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been sent to the peer.
     *         The delivery contained in the future will represent the delivery
     *         state at the time the future has been succeeded, i.e. it will be
     *         locally <em>unsettled</em> without any outcome yet.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final Future<ProtonDelivery> send(final Message message, final Span currentSpan) {
        return senderLink.send(message, currentSpan);
    }

    /**
     * Sends a message to the AMQP adapter and waits for the disposition indicating the outcome of the transfer.
     * <p>
     * A not-accepted outcome will cause the returned future to be failed.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been accepted (and settled)
     *         by the peer.
     *         <p>
     *         The future will be failed with a {@link ServerErrorException} if the message
     *         could not be sent due to a lack of credit.
     *         If a message is sent which cannot be processed by the peer, the future will
     *         be failed with either a {@link ServerErrorException} or a {@link ClientErrorException}
     *         depending on the reason for the failure to process the message.
     *         If no delivery update was received from the peer within the configured timeout period
     *         (see {@link ClientConfigProperties#getSendMessageTimeout()}), the future will
     *         be failed with a {@link ServerErrorException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final Span currentSpan) {
        return senderLink.sendAndWaitForOutcome(message, currentSpan);
    }

    @Override
    public final Future<Void> close() {
        return senderLink.close();
    }

    @Override
    public final boolean isOpen() {
        return senderLink.isOpen();
    }
}
