/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TelemetryConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for uploading telemetry data to a Hono server.
 */
public class TelemetrySenderImpl extends AbstractDownstreamSender {

    /**
     * Creates a telemetry sender instance for a given connection and proton sender.
     *
     * @param con The open connection to the Hono server.
     * @param sender The sender link to send telemetry messages over.
     * @param tenantId The tenant that the messages will be published for.
     * @param targetAddress The target address to send the messages to.
     * @param sampler The sampler for sending messages.
     */
    protected TelemetrySenderImpl(
            final HonoConnection con,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress,
            final SendMessageSampler sampler) {

        super(con, sender, tenantId, targetAddress, sampler);
    }

    @Override
    public String getEndpoint() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    @Override
    protected String getTo(final String deviceId) {
        return AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, deviceId, null);
    }

    /**
     * Creates a new sender for publishing telemetry data to a Hono server.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the telemetry data will be published for.
     * @param sampler The sampler to use.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if con or tenantId is {@code null}.
     */
    public static Future<DownstreamSender> create(
            final HonoConnection con,
            final String tenantId,
            final SendMessageSampler sampler,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);

        final String targetAddress = AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, null, con.getConfig());
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .compose(sender -> Future
                        .succeededFuture(new TelemetrySenderImpl(con, sender, tenantId, targetAddress, sampler)));
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
        TracingHelper.setDeviceTags(span, tenantId, MessageHelper.getDeviceId(rawMessage));
        TracingHelper.injectSpanContext(connection.getTracer(), span.context(), rawMessage);

        return connection.executeOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServiceInvocationException e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available");
                logMessageSendingError("error sending message [ID: {}, address: {}], no credit available",
                        rawMessage.getMessageId(), getMessageAddress(rawMessage));
                logError(span, e);
                span.finish();
                result.fail(e);
            } else {
                sendMessageAndWaitForOutcome(rawMessage, span).onComplete(result);
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
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final SendMessageSampler.Sample sample = this.sampler.start(this.tenantId);

        final ClientConfigProperties config = connection.getConfig();
        final AtomicBoolean timeoutReached = new AtomicBoolean(false);
        final Long timerId = config.getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(config.getSendMessageTimeout(), id -> {
                    if (timeoutReached.compareAndSet(false, true)) {
                        final ServerErrorException exception = new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE,
                                "waiting for delivery update timed out after " + config.getSendMessageTimeout() + "ms");
                        logMessageSendingError(
                                "waiting for delivery update timed out for message [ID: {}, address: {}] after {}ms",
                                messageId, getMessageAddress(message), connection.getConfig().getSendMessageTimeout());
                        TracingHelper.logError(currentSpan, exception.getMessage());
                        Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_UNAVAILABLE);
                        currentSpan.finish();
                        sample.timeout();
                    }
                })
                : null;

        final ProtonDelivery result = sender.send(message, deliveryUpdated -> {
            if (timerId != null) {
                connection.getVertx().cancelTimer(timerId);
            }
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            sample.completed(remoteState);
            if (timeoutReached.get()) {
                log.debug("ignoring received delivery update for message [ID: {}, address: {}]: waiting for the update has already timed out",
                        messageId, getMessageAddress(message));
            } else if (deliveryUpdated.remotelySettled()) {
                logUpdatedDeliveryState(currentSpan, message, deliveryUpdated);
            } else {
                logMessageSendingError("peer did not settle message [ID: {}, address: {}, remote state: {}], failing delivery",
                        messageId, getMessageAddress(message), remoteState.getClass().getSimpleName());
                TracingHelper.logError(currentSpan, new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery"));
            }
            currentSpan.finish();
        });
        log.trace("sent message [ID: {}, address: {}], remaining credit: {}, queued messages: {}", messageId,
                getMessageAddress(message), sender.getCredit(), sender.getQueued());

        return Future.succeededFuture(result);
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        final Span span = newFollowingSpan(parent, "forward Telemetry data");
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
        return span;
    }

    private Span startChildSpan(final SpanContext parent, final Message rawMessage) {

        final Span span = newChildSpan(parent, "forward Telemetry data");
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
        return span;
    }
}
