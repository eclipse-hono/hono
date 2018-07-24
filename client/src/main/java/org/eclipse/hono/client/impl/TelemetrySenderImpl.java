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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.MessageAnnotationsInjectAdapter;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TelemetryConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
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
 * A Vertx-Proton based client for uploading telemetry data to a Hono server.
 */
public final class TelemetrySenderImpl extends AbstractSender {

    TelemetrySenderImpl(final ClientConfigProperties config, final ProtonSender sender, final String tenantId,
            final String targetAddress, final Context context) {
        this(config, sender, tenantId, targetAddress, context, null);
    }

    TelemetrySenderImpl(final ClientConfigProperties config, final ProtonSender sender, final String tenantId,
            final String targetAddress, final Context context, final Tracer tracer) {
        super(config, sender, tenantId, targetAddress, context, tracer);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for uploading data to Hono's telemetry endpoint.
     * 
     * @param tenantId The tenant to upload data for.
     * @param deviceId The device to upload data for. If {@code null}, the target address can be used
     *                 to upload data for arbitrary devices belonging to the tenant.
     * @return The target address.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static String getTargetAddress(final String tenantId, final String deviceId) {
        final StringBuilder targetAddress = new StringBuilder(TelemetryConstants.TELEMETRY_ENDPOINT)
                .append("/").append(Objects.requireNonNull(tenantId));
        if (deviceId != null && deviceId.length() > 0) {
            targetAddress.append("/").append(deviceId);
        }
        return targetAddress.toString();
    }

    @Override
    public String getEndpoint() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    @Override
    protected String getTo(final String deviceId) {
        return getTargetAddress(tenantId, deviceId);
    }

    /**
     * Creates a new sender for publishing telemetry data to a Hono server.
     * 
     * @param context The vertx context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the telemetry data will be uploaded for.
     * @param deviceId The device that the telemetry data will be uploaded for or {@code null}
     *                 if the data to be uploaded will be produced by arbitrary devices of the
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
                    new TelemetrySenderImpl(clientConfig, sender, tenantId, targetAddress, context, tracer));
        }).setHandler(creationHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message rawMessage) {

        return sendAndWaitForOutcome(rawMessage, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message rawMessage, final SpanContext parent) {

        Objects.requireNonNull(rawMessage);

        // we create a child span (instead of a following span) because we depend
        // on the outcome of the sending operation
        final Span span = startChildSpan(parent, rawMessage);
        Tags.MESSAGE_BUS_DESTINATION.set(span, targetAddress);
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, MessageHelper.getDeviceId(rawMessage));
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new MessageAnnotationsInjectAdapter(rawMessage));

        if (!isRegistrationAssertionRequired()) {
            MessageHelper.getAndRemoveRegistrationAssertion(rawMessage);
        }
        return executeOrRunOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServiceInvocationException e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available");
                logError(span, e);
                span.finish();
                result.fail(e);
            } else {
                sendMessageAndWaitForOutcome(rawMessage, span).setHandler(result.completer());
            }
        });
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for.
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
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(currentSpan);

        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        final Map<String, Object> details = new HashMap<>(3);
        details.put(TracingHelper.TAG_MESSAGE_ID.getKey(), messageId);
        details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
        details.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().toString());
        currentSpan.log(details);

        final ProtonDelivery result = sender.send(message, deliveryUpdated -> {
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            if (deliveryUpdated.remotelySettled()) {
                if (Accepted.class.isInstance(remoteState)) {
                    LOG.trace("message [message ID: {}] accepted by peer", messageId);
                    currentSpan.log("message accepted by peer");
                    Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
                } else {
                    final Map<String, Object> events = new HashMap<>();
                    if (Rejected.class.isInstance(remoteState)) {
                        final Rejected rejected = (Rejected) deliveryUpdated.getRemoteState();
                        Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_BAD_REQUEST);
                        if (rejected.getError() == null) {
                            LOG.debug("message [message ID: {}] rejected by peer", messageId);
                        } else {
                            LOG.debug("message [message ID: {}] rejected by peer: {}, {}", messageId,
                                    rejected.getError().getCondition(), rejected.getError().getDescription());
                            events.put(Fields.MESSAGE, rejected.getError().getDescription());
                        }
                    } else {
                        LOG.debug("message [message ID: {}] not accepted by peer: {}",
                                messageId, remoteState.getClass().getSimpleName());
                        Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_UNAVAILABLE);
                    }
                    TracingHelper.logError(currentSpan, events);
                }
            } else {
                LOG.warn("peer did not settle message [message ID: {}, remote state: {}]",
                        messageId, remoteState.getClass().getSimpleName());
                TracingHelper.logError(currentSpan, new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery"));
            }
            currentSpan.finish();
        });
        LOG.trace("sent message [ID: {}], remaining credit: {}, queued messages: {}", messageId, sender.getCredit(), sender.getQueued());

        return Future.succeededFuture(result);
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        if (tracer == null) {
            throw new IllegalStateException("no tracer configured");
        } else {
            final Span span = newFollowingSpan(parent, "forward Telemetry data");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
            return span;
        }
    }

    private Span startChildSpan(final SpanContext parent, final Message rawMessage) {

        if (tracer == null) {
            throw new IllegalStateException("no tracer configured");
        } else {
            final Span span = newChildSpan(parent, "forward Telemetry data");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
            return span;
        }
    }
}
