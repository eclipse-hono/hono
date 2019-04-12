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

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.EventConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing event messages to a Hono server.
 */
public final class EventSenderImpl extends AbstractDownstreamSender {

    EventSenderImpl(
            final HonoConnection con,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress) {

        super(con, sender, tenantId, targetAddress);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending messages to Hono's event endpoint.
     * 
     * @param tenantId The tenant to send events for.
     * @param deviceId The device to send events for. If {@code null}, the target address can be used
     *                 to send events for arbitrary devices belonging to the tenant.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId, final String deviceId) {
        final StringBuilder address = new StringBuilder(EventConstants.EVENT_ENDPOINT).append("/").append(tenantId);
        if (deviceId != null && deviceId.length() > 0) {
            address.append("/").append(deviceId);
        }
        return address.toString();
    }

    @Override
    public String getEndpoint() {
        return EventConstants.EVENT_ENDPOINT;
    }

    @Override
    protected String getTo(final String deviceId) {
        return getTargetAddress(tenantId, deviceId);
    }

    /**
     * Creates a new sender for publishing events to a Hono server.
     * 
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the events will be published for.
     * @param closeHook The handler to invoke when the Hono server closes the sender. The sender's
     *                  target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of context, connection, tenant or handler is {@code null}.
     */
    public static Future<DownstreamSender> create(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);

        final String targetAddress = getTargetAddress(tenantId, null);
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook)
        .compose(sender -> Future.succeededFuture(new EventSenderImpl(con, sender, tenantId, targetAddress)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method simply invokes {@link #send(Message)} because events are
     * always sent with at least once delivery semantics.
     */
    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {

        return send(message);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method simply invokes {@link #send(Message, SpanContext)} because events are
     * always sent with at least once delivery semantics.
     */
    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext parent) {

        return send(message, parent);
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for
     * and waits for the outcome of the transfer.
     * <p>
     * This method sets the message's <em>durable</em> property to {@code true} and
     * then invokes {@link #sendMessageAndWaitForOutcome(Message, Span)}.
     * 
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled)
     *         by the peer.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent or has not been accepted by the peer or if no delivery update
     *         was received from the peer within the configured timeout period
     *         (see {@link ClientConfigProperties#getSendMessageTimeout()}).
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {

        message.setDurable(true);
        return sendMessageAndWaitForOutcome(message, currentSpan);
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        final Span span = newChildSpan(parent, "forward Event");
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
        return span;
    }
}
