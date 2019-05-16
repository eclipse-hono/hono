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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing messages to Hono.
 */
public abstract class AbstractSender extends AbstractHonoClient implements MessageSender {

    /**
     * A counter to be used for creating message IDs.
     */
    protected static final AtomicLong MESSAGE_COUNTER = new AtomicLong();

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());
    /**
     * The identifier of the tenant that the devices belong to which have published the messages
     * that this sender is used to send downstream.
     */
    protected final String tenantId;
    /**
     * The target address that this sender sends messages to.
     */
    protected final String targetAddress;

    private Handler<Void> drainHandler;

    /**
     * Creates a new sender.
     * 
     * @param connection The connection to use for interacting with the server.
     * @param sender The sender link to send messages over.
     * @param tenantId The identifier of the tenant that the
     *           devices belong to which have published the messages
     *           that this sender is used to send downstream.
     * @param targetAddress The target address to send the messages to.
     */
    protected AbstractSender(
            final HonoConnection connection,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress) {

        super(connection);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.targetAddress = targetAddress;
        if (sender.isOpen()) {
            this.offeredCapabilities = Optional.ofNullable(sender.getRemoteOfferedCapabilities())
                    .map(caps -> Collections.unmodifiableList(Arrays.asList(caps)))
                    .orElse(Collections.emptyList());
        }
    }

    @Override
    public final int getCredit() {
        if (sender == null) {
            return 0;
        } else {
            return sender.getCredit();
        }
    }

    @Override
    public final void sendQueueDrainHandler(final Handler<Void> handler) {
        if (this.drainHandler != null) {
            throw new IllegalStateException("already waiting for replenishment with credit");
        } else {
            this.drainHandler = Objects.requireNonNull(handler);
            sender.sendQueueDrainHandler(replenishedSender -> {
                LOG.trace("sender has received FLOW [credits: {}, queued:{}]", replenishedSender.getCredit(), replenishedSender.getQueued());
                final Handler<Void> currentHandler = this.drainHandler;
                this.drainHandler = null;
                if (currentHandler != null) {
                    currentHandler.handle(null);
                }
            });
        }
    }

    @Override
    public final void close(final Handler<AsyncResult<Void>> closeHandler) {
        Objects.requireNonNull(closeHandler);
        LOG.debug("closing sender ...");
        closeLinks(ok -> closeHandler.handle(Future.succeededFuture()));
    }

    @Override
    public final boolean isOpen() {
        return sender.isOpen();
    }

    @Override
    public final Future<ProtonDelivery> send(final Message rawMessage) {

        return send(rawMessage, (SpanContext) null);
    }

    @Override
    public final Future<ProtonDelivery> send(final Message rawMessage, final SpanContext parent) {

        Objects.requireNonNull(rawMessage);

        final Span span = startSpan(parent, rawMessage);
        Tags.MESSAGE_BUS_DESTINATION.set(span, targetAddress);
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, MessageHelper.getDeviceId(rawMessage));
        TracingHelper.injectSpanContext(connection.getTracer(), span.context(), rawMessage);

        return connection.executeOrRunOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServiceInvocationException e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available");
                logError(span, e);
                span.finish();
                result.fail(e);
            } else {
                sendMessage(rawMessage, span).setHandler(result);
            }
        });
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for.
     * <p>
     * The message is sent according to the delivery semantics defined by
     * the Hono API this client interacts with.
     * 
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent to the endpoint.
     *         The delivery contained in the future represents the delivery state at the time
     *         the future has been succeeded, i.e. for telemetry data it will be locally
     *         <em>unsettled</em> without any outcome yet. For events and commands it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent or, in the case of events or commands, if no delivery update
     *         was received from the peer within the configured timeout period
     *         (see {@link ClientConfigProperties#getSendMessageTimeout()}).
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected abstract Future<ProtonDelivery> sendMessage(Message message, Span currentSpan);

    /**
     * Creates and starts a new OpenTracing {@code Span} for a message to be sent.
     * 
     * @param message The message to create the span for.
     * @return The started span.
     * @throws NullPointerException if message is {@code null}.
     */
    protected final Span startSpan(final Message message) {
        return startSpan(null, message);
    }

    /**
     * Creates and starts a new OpenTracing {@code Span} for a message to be sent
     * in an existing context.
     * 
     * @param context The context to create the span in. If {@code null}, then
     *                  the span is created without a parent.
     * @param message The message to create the span for.
     * @return The started span.
     * @throws NullPointerException if message is {@code null}.
     */
    protected abstract Span startSpan(SpanContext context, Message message);

    /**
     * Gets the value of the <em>to</em> property to be used for messages produced by this sender.
     * 
     * @param deviceId The identifier of the device that the message's content originates from.
     * @return The address.
     */
    protected abstract String getTo(String deviceId);

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for
     * and waits for the outcome of the transfer.
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
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    protected Future<ProtonDelivery> sendMessageAndWaitForOutcome(final Message message, final Span currentSpan) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(currentSpan);

        final Future<ProtonDelivery> result = Future.future();
        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final Long timerId = connection.getConfig().getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(connection.getConfig().getSendMessageTimeout(), id -> {
                    if (!result.isComplete()) {
                        final ServerErrorException exception = new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE,
                                "waiting for delivery update timed out after " + connection.getConfig().getSendMessageTimeout() + "ms");
                        LOG.debug("waiting for delivery update timed out for message [message ID: {}] after {}ms",
                                messageId, connection.getConfig().getSendMessageTimeout());
                        result.fail(exception);
                    }
                })
                : null;

        sender.send(message, deliveryUpdated -> {
            if (timerId != null) {
                connection.getVertx().cancelTimer(timerId);
            }
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            if (result.isComplete()) {
                LOG.debug("ignoring received delivery update for message [message ID: {}]: waiting for the update has already timed out", messageId);
            } else if (deliveryUpdated.remotelySettled()) {
                logUpdatedDeliveryState(currentSpan, messageId, deliveryUpdated);
                if (Accepted.class.isInstance(remoteState)) {
                    result.complete(deliveryUpdated);
                } else {
                    ServiceInvocationException e = null;
                    if (Rejected.class.isInstance(remoteState)) {
                        final Rejected rejected = (Rejected) remoteState;
                        e = rejected.getError() == null
                                ? new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)
                                : new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, rejected.getError().getDescription());
                    } else if (Released.class.isInstance(remoteState)) {
                        e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
                    } else if (Modified.class.isInstance(remoteState)) {
                        final Modified modified = (Modified) deliveryUpdated.getRemoteState();
                        e = modified.getUndeliverableHere() ? new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)
                                : new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
                    }
                    result.fail(e);
                }
            } else {
                LOG.debug("peer did not settle message [message ID: {}, remote state: {}], failing delivery",
                        messageId, remoteState.getClass().getSimpleName());
                final ServiceInvocationException e = new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery");
                result.fail(e);
            }
        });
        LOG.trace("sent message [ID: {}], remaining credit: {}, queued messages: {}", messageId, sender.getCredit(), sender.getQueued());

        return result.map(delivery -> {
            LOG.trace("message [ID: {}] accepted by peer", messageId);
            Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
            currentSpan.finish();
            return delivery;
        }).recover(t -> {
            TracingHelper.logError(currentSpan, t);
            Tags.HTTP_STATUS.set(currentSpan, ServiceInvocationException.extractStatusCode(t));
            currentSpan.finish();
            return Future.failedFuture(t);
        });
    }

    /**
     * Creates a log entry in the given span with the message id and information about the sender (credits, QOS).
     * 
     * @param currentSpan The current span to log to.
     * @param messageId The message id.
     * @throws NullPointerException if currentSpan is {@code null}.
     */
    protected final void logMessageIdAndSenderInfo(final Span currentSpan, final String messageId) {
        final Map<String, Object> details = new HashMap<>(3);
        details.put(TracingHelper.TAG_MESSAGE_ID.getKey(), messageId);
        details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
        details.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().toString());
        currentSpan.log(details);
    }

    /**
     * Creates a log entry in the given span with information about the message delivery outcome given in the delivery
     * parameter. Sets the {@link Tags#HTTP_STATUS} as well.
     * <p>
     * Also corresponding log output is created.
     *
     * @param currentSpan The current span to log to.
     * @param messageId The message id.
     * @param delivery The updated delivery.
     * @throws NullPointerException if currentSpan or delivery is {@code null}.
     */
    protected final void logUpdatedDeliveryState(final Span currentSpan, final String messageId, final ProtonDelivery delivery) {
        Objects.requireNonNull(currentSpan);
        final DeliveryState remoteState = delivery.getRemoteState();
        if (Accepted.class.isInstance(remoteState)) {
            LOG.trace("message [message ID: {}] accepted by peer", messageId);
            currentSpan.log("message accepted by peer");
            Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
        } else {
            final Map<String, Object> events = new HashMap<>();
            if (Rejected.class.isInstance(remoteState)) {
                final Rejected rejected = (Rejected) delivery.getRemoteState();
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_BAD_REQUEST);
                if (rejected.getError() == null) {
                    LOG.debug("message [message ID: {}] rejected by peer", messageId);
                    events.put(Fields.MESSAGE, "message rejected by peer");
                } else {
                    LOG.debug("message [message ID: {}] rejected by peer: {}, {}", messageId,
                            rejected.getError().getCondition(), rejected.getError().getDescription());
                    events.put(Fields.MESSAGE, String.format("message rejected by peer: %s, %s",
                            rejected.getError().getCondition(), rejected.getError().getDescription()));
                }
            } else if (Released.class.isInstance(remoteState)) {
                LOG.debug("message [message ID: {}] not accepted by peer, remote state: {}",
                        messageId, remoteState.getClass().getSimpleName());
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_UNAVAILABLE);
                events.put(Fields.MESSAGE, "message not accepted by peer, remote state: " + remoteState);
            } else if (Modified.class.isInstance(remoteState)) {
                final Modified modified = (Modified) delivery.getRemoteState();
                LOG.debug("message [message ID: {}] not accepted by peer, remote state: {}",
                        messageId, modified);
                Tags.HTTP_STATUS.set(currentSpan, modified.getUndeliverableHere() ? HttpURLConnection.HTTP_NOT_FOUND
                        : HttpURLConnection.HTTP_UNAVAILABLE);
                events.put(Fields.MESSAGE, "message not accepted by peer, remote state: " + remoteState);
            }
            TracingHelper.logError(currentSpan, events);
        }
    }
}
