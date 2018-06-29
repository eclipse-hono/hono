/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 *    Bosch Software Innovations GmbH - add Open Tracing support
 */

package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.EventConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing event messages to a Hono server.
 */
public final class EventSenderImpl extends AbstractSender {

    EventSenderImpl(final ClientConfigProperties config, final ProtonSender sender, final String tenantId,
            final String targetAddress, final Context context) {
        this(config, sender, tenantId, targetAddress, context, null);
    }

    EventSenderImpl(final ClientConfigProperties config, final ProtonSender sender, final String tenantId,
            final String targetAddress, final Context context, final Tracer tracer) {
        super(config, sender, tenantId, targetAddress, context, tracer);
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
     * @param context The vertx context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the events will be published for.
     * @param deviceId The device that the events will be published for or {@code null}
     *                 if the events are going to be be produced by arbitrary devices of the
     *                 tenant.
     * @param closeHook The handler to invoke when the Hono server closes the sender. The sender's
     *                  target address is provided as an argument to the handler.
     * @param creationHandler The handler to invoke with the result of the creation attempt.
     * @param tracer The <em>OpenTracing</em> {@code Tracer} to keep track of the messages sent
     *               by the sender returned.
     * @throws NullPointerException if any of context, connection, tenant or handler is {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<String> closeHook,
            final Handler<AsyncResult<MessageSender>> creationHandler,
            final Tracer tracer) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(creationHandler);

        final String targetAddress = getTargetAddress(tenantId, deviceId);
        createSender(context, clientConfig, con, targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook).compose(sender -> {
            return Future.<MessageSender> succeededFuture(
                    new EventSenderImpl(clientConfig, sender, tenantId, targetAddress, context, tracer));
        }).setHandler(creationHandler);
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
     * Sets the <em>durable</em> message property to {@code true}.
     */
    @Override
    protected void addEndpointSpecificProperties(final Message msg, final String deviceId) {
        msg.setDurable(true);
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for
     * and waits for the outcome of the transfer.
     * <p>
     * This method simply invokes {@link #sendMessageAndWaitForOutcome(Message, Span)}.
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
     *         message could not be sent or has not been accepted by the peer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {

        return sendMessageAndWaitForOutcome(message, currentSpan);
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        if (tracer == null) {
            throw new IllegalStateException("no tracer configured");
        } else {
            final Span span = newChildSpan(parent, "forward Event");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
            return span;
        }
    }
}
