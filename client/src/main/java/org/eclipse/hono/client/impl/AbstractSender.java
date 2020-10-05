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
import org.eclipse.hono.client.MessageNotProcessedException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.MessageUndeliverableException;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.SendMessageTimeoutException;
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
import io.vertx.core.Promise;
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
    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * The identifier of the tenant that the devices belong to which have published the messages
     * that this sender is used to send downstream.
     */
    protected final String tenantId;
    /**
     * The target address that this sender sends messages to.
     */
    protected final String targetAddress;
    /**
     * A sampler for sending messages.
     */
    protected final SendMessageSampler sampler;

    private Handler<Void> drainHandler;

    /**
     * Creates a new sender.
     *
     * @param connection The connection to use for interacting with the server.
     * @param sender The sender link to send messages over.
     * @param tenantId The identifier of the tenant that the
     *           devices belong to which have published the messages
     *           that this sender is used to send downstream.
     * @param sampler The sampler for sending messages.
     * @param targetAddress The target address to send the messages to.
     */
    protected AbstractSender(
            final HonoConnection connection,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress,
            final SendMessageSampler sampler) {

        super(connection);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.targetAddress = targetAddress;
        this.sampler = sampler;
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
                log.trace("sender has received FLOW [credits: {}, queued:{}]", replenishedSender.getCredit(), replenishedSender.getQueued());
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
        log.debug("closing sender ...");
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
        TracingHelper.TAG_QOS.set(span, sender.getQoS().toString());
        TracingHelper.setDeviceTags(span, tenantId, MessageHelper.getDeviceId(rawMessage));
        TracingHelper.injectSpanContext(connection.getTracer(), span.context(), rawMessage);

        return connection.executeOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServerErrorException e = new NoConsumerException("no credit available");
                logMessageSendingError("error sending message [ID: {}, address: {}], no credit available (drain={})",
                        rawMessage.getMessageId(), getMessageAddress(rawMessage), sender.getDrain());
                TracingHelper.TAG_CREDIT.set(span, 0);
                logError(span, e);
                span.finish();
                result.fail(e);
                this.sampler.queueFull(this.tenantId);
            } else {
                sendMessage(rawMessage, span).onComplete(result);
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

        final Promise<ProtonDelivery> result = Promise.promise();
        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final SendMessageSampler.Sample sample = this.sampler.start(this.tenantId);

        final Long timerId = connection.getConfig().getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(connection.getConfig().getSendMessageTimeout(), id -> {
                    if (!result.future().isComplete()) {
                        final ServerErrorException exception = new SendMessageTimeoutException(
                                "waiting for delivery update timed out after "
                                        + connection.getConfig().getSendMessageTimeout() + "ms");
                        logMessageSendingError("waiting for delivery update timed out for message [ID: {}, address: {}] after {}ms",
                                messageId, getMessageAddress(message), connection.getConfig().getSendMessageTimeout());
                        result.fail(exception);
                        sample.timeout();
                    }
                })
                : null;

        sender.send(message, deliveryUpdated -> {
            if (timerId != null) {
                connection.getVertx().cancelTimer(timerId);
            }
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            if (result.future().isComplete()) {
                log.debug("ignoring received delivery update for message [ID: {}, address: {}]: waiting for the update has already timed out",
                        messageId, getMessageAddress(message));
            } else if (deliveryUpdated.remotelySettled()) {
                logUpdatedDeliveryState(currentSpan, message, deliveryUpdated);
                sample.completed(remoteState);
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
                        e = new MessageNotProcessedException();
                    } else if (Modified.class.isInstance(remoteState)) {
                        final Modified modified = (Modified) deliveryUpdated.getRemoteState();
                        if (modified.getUndeliverableHere()) {
                            e = new MessageUndeliverableException();
                        } else {
                            e = new MessageNotProcessedException();
                        }
                    }
                    result.fail(e);
                }
            } else {
                logMessageSendingError("peer did not settle message [ID: {}, address: {}, remote state: {}], failing delivery",
                        messageId, getMessageAddress(message), remoteState.getClass().getSimpleName());
                final ServiceInvocationException e = new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery");
                result.fail(e);
            }
        });
        log.trace("sent message [ID: {}, address: {}], remaining credit: {}, queued messages: {}", messageId,
                getMessageAddress(message), sender.getCredit(), sender.getQueued());

        return result.future()
                .map(delivery -> {
                    log.trace("message [ID: {}, address: {}] accepted by peer", messageId, getMessageAddress(message));
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
        final Map<String, Object> details = new HashMap<>(2);
        details.put(TracingHelper.TAG_MESSAGE_ID.getKey(), messageId);
        details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
        currentSpan.log(details);
    }

    /**
     * Creates a log entry in the given span with information about the message delivery outcome given in the delivery
     * parameter. Sets the {@link Tags#HTTP_STATUS} as well.
     * <p>
     * Also corresponding log output is created.
     *
     * @param currentSpan The current span to log to.
     * @param message The message.
     * @param delivery The updated delivery.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void logUpdatedDeliveryState(final Span currentSpan, final Message message, final ProtonDelivery delivery) {
        Objects.requireNonNull(currentSpan);
        final String messageId = message.getMessageId() != null ? message.getMessageId().toString() : "";
        final String messageAddress = getMessageAddress(message);
        final DeliveryState remoteState = delivery.getRemoteState();
        if (Accepted.class.isInstance(remoteState)) {
            log.trace("message [ID: {}, address: {}] accepted by peer", messageId, messageAddress);
            currentSpan.log("message accepted by peer");
            Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_ACCEPTED);
        } else {
            final Map<String, Object> events = new HashMap<>();
            if (Rejected.class.isInstance(remoteState)) {
                final Rejected rejected = (Rejected) delivery.getRemoteState();
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_BAD_REQUEST);
                if (rejected.getError() == null) {
                    logMessageSendingError("message [ID: {}, address: {}] rejected by peer", messageId, messageAddress);
                    events.put(Fields.MESSAGE, "message rejected by peer");
                } else {
                    logMessageSendingError("message [ID: {}, address: {}] rejected by peer: {}, {}", messageId,
                            messageAddress, rejected.getError().getCondition(), rejected.getError().getDescription());
                    events.put(Fields.MESSAGE, String.format("message rejected by peer: %s, %s",
                            rejected.getError().getCondition(), rejected.getError().getDescription()));
                }
            } else if (Released.class.isInstance(remoteState)) {
                logMessageSendingError("message [ID: {}, address: {}] not accepted by peer, remote state: {}",
                        messageId, messageAddress, remoteState.getClass().getSimpleName());
                Tags.HTTP_STATUS.set(currentSpan, HttpURLConnection.HTTP_UNAVAILABLE);
                events.put(Fields.MESSAGE, "message not accepted by peer, remote state: " + remoteState);
            } else if (Modified.class.isInstance(remoteState)) {
                final Modified modified = (Modified) delivery.getRemoteState();
                logMessageSendingError("message [ID: {}, address: {}] not accepted by peer, remote state: {}",
                        messageId, messageAddress, modified);
                Tags.HTTP_STATUS.set(currentSpan, modified.getUndeliverableHere() ? HttpURLConnection.HTTP_NOT_FOUND
                        : HttpURLConnection.HTTP_UNAVAILABLE);
                events.put(Fields.MESSAGE, "message not accepted by peer, remote state: " + remoteState);
            }
            TracingHelper.logError(currentSpan, events);
        }
    }

    /**
     * Gets the address the message is targeted at.
     * <p>
     * This is either the message address or the link address of this sender.
     *
     * @param message The message.
     * @return The message or link address.
     * @throws NullPointerException if message is {@code null}.
     */
    protected final String getMessageAddress(final Message message) {
        Objects.requireNonNull(message);
        return message.getAddress() != null ? message.getAddress() : targetAddress;
    }

    /**
     * Logs an error that occurred when sending a message.
     * <p>
     * This method logs on DEBUG level by default. Subclasses may override this
     * method to use a different log level.
     *
     * @param format The log format string.
     * @param arguments The arguments of the format string.
     */
    protected void logMessageSendingError(final String format, final Object... arguments) {
        log.debug(format, arguments);
    }
}
