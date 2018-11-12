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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.MessageAnnotationsInjectAdapter;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing messages to Hono.
 */
abstract public class AbstractSender extends AbstractHonoClient implements MessageSender {

    /**
     * A counter to be used for creating message IDs.
     */
    protected static final AtomicLong MESSAGE_COUNTER = new AtomicLong();

    private static final Pattern CHARSET_PATTERN = Pattern.compile("^.*;charset=(.*)$");

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
    private boolean registrationAssertionRequired;

    /**
     * Creates a new sender.
     * 
     * @param config The configuration properties to use.
     * @param sender The sender link to send messages over.
     * @param tenantId The identifier of the tenant that the
     *           devices belong to which have published the messages
     *           that this sender is used to send downstream.
     * @param targetAddress The target address to send the messages to.
     * @param context The vert.x context to use for sending the messages.
     * @param tracer The tracer to use.
     */
    protected AbstractSender(
            final ClientConfigProperties config,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress,
            final Context context,
            final Tracer tracer) {

        super(context, config, tracer);
        this.sender = Objects.requireNonNull(sender);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.targetAddress = targetAddress;
        if (sender.isOpen()) {
            this.offeredCapabilities = Optional.ofNullable(sender.getRemoteOfferedCapabilities())
                    .map(caps -> Collections.unmodifiableList(Arrays.asList(caps)))
                    .orElse(Collections.emptyList());
            this.registrationAssertionRequired = supportsCapability(Constants.CAP_REG_ASSERTION_VALIDATION);
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
    @Deprecated
    public final boolean sendQueueFull() {
        return sender.sendQueueFull();
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
    @Deprecated
    public final Future<ProtonDelivery> send(final Message rawMessage, final Handler<Void> capacityAvailableHandler) {

        Objects.requireNonNull(rawMessage);

        return executeOrRunOnContext(result -> {
            if (capacityAvailableHandler == null) {
                final Span currentSpan = startSpan(rawMessage);
                sendMessage(rawMessage, currentSpan).setHandler(result.completer());
            } else if (this.drainHandler != null) {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "cannot send message while waiting for replenishment with credit"));
            } else if (sender.isOpen()) {
                final Span currentSpan = startSpan(rawMessage);
                sendMessage(rawMessage, currentSpan).setHandler(result.completer());
                if (sender.sendQueueFull()) {
                    sendQueueDrainHandler(capacityAvailableHandler);
                } else {
                    capacityAvailableHandler.handle(null);
                }
            } else {
                result.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE,
                        "send link to peer is closed"));
            }
        });
    }

    @Override
    public final Future<ProtonDelivery> send(final Message rawMessage) {

        return send(rawMessage, (SpanContext) null);
    }

    @Override
    public final Future<ProtonDelivery> send(final Message rawMessage, final SpanContext parent) {

        Objects.requireNonNull(rawMessage);

        if (!isRegistrationAssertionRequired()) {
            MessageHelper.getAndRemoveRegistrationAssertion(rawMessage);
        }

        final Span span = startSpan(parent, rawMessage);
        Tags.MESSAGE_BUS_DESTINATION.set(span, targetAddress);
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, MessageHelper.getDeviceId(rawMessage));
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new MessageAnnotationsInjectAdapter(rawMessage));

        return executeOrRunOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServiceInvocationException e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available");
                logError(span, e);
                span.finish();
                result.fail(e);
            } else {
                sendMessage(rawMessage, span).setHandler(result.completer());
            }
        });
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion) {
        return send(deviceId, null, payload, contentType, registrationAssertion);
    }

    @Override
    @Deprecated
    public final Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        return send(deviceId, null, payload, contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final String payload, final String contentType, final String registrationAssertion) {
        return send(deviceId, null, payload, contentType, registrationAssertion);
    }

    @Override
    @Deprecated
    public final Future<ProtonDelivery> send(final String deviceId, final String payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        return send(deviceId, null, payload, contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties, final String payload, final String contentType,
            final String registrationAssertion) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion);
    }

    @Override
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties, final byte[] payload, final String contentType,
                              final String registrationAssertion) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(registrationAssertion);

        final Message msg = ProtonHelper.message();
        msg.setAddress(getTo(deviceId));
        MessageHelper.setPayload(msg, contentType, payload);
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId, registrationAssertion);
        return send(msg);
    }

    @Override
    @Deprecated
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties,
            final String payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {
        Objects.requireNonNull(payload);
        final Charset charset = getCharsetForContentType(Objects.requireNonNull(contentType));
        return send(deviceId, properties, payload.getBytes(charset), contentType, registrationAssertion, capacityAvailableHandler);
    }

    @Override
    @Deprecated
    public final Future<ProtonDelivery> send(final String deviceId, final Map<String, ?> properties,
            final byte[] payload, final String contentType, final String registrationAssertion,
            final Handler<Void> capacityAvailableHandler) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(registrationAssertion);

        final Message msg = ProtonHelper.message();
        msg.setAddress(getTo(deviceId));
        MessageHelper.setPayload(msg, contentType, payload);
        setApplicationProperties(msg, properties);
        addProperties(msg, deviceId, registrationAssertion);
        return send(msg, capacityAvailableHandler);
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
     *         <em>unsettled</em> without any outcome yet. For events it will be locally
     *         and remotely <em>settled</em> and will contain the <em>accepted</em> outcome.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent.
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

    private void addProperties(final Message msg, final String deviceId, final String registrationAssertion) {
        MessageHelper.addDeviceId(msg, deviceId);
        if (isRegistrationAssertionRequired()) {
            MessageHelper.addRegistrationAssertion(msg, registrationAssertion);
        }
    }

    private Charset getCharsetForContentType(final String contentType) {

        final Matcher m = CHARSET_PATTERN.matcher(contentType);
        if (m.matches()) {
            return Charset.forName(m.group(1));
        } else {
            return StandardCharsets.UTF_8;
        }
    }

    @Override
    public final boolean isRegistrationAssertionRequired() {
        return registrationAssertionRequired;
    }

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
     *         message could not be sent or has not been accepted by the peer.
     * @throws NullPointerException if the message is {@code null}.
     */
    protected Future<ProtonDelivery> sendMessageAndWaitForOutcome(final Message message, final Span currentSpan) {

        Objects.requireNonNull(message);

        final Future<ProtonDelivery> result = Future.future();
        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        final Map<String, Object> details = new HashMap<>(2);
        details.put(TracingHelper.TAG_MESSAGE_ID.getKey(), messageId);
        details.put(TracingHelper.TAG_CREDIT.getKey(), sender.getCredit());
        details.put(TracingHelper.TAG_QOS.getKey(), sender.getQoS().toString());
        currentSpan.log(details);

        sender.send(message, deliveryUpdated -> {
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            if (deliveryUpdated.remotelySettled()) {
                if (Accepted.class.isInstance(remoteState)) {
                    currentSpan.log("message accepted by peer");
                    result.complete(deliveryUpdated);
                } else {
                    ServiceInvocationException e = null;
                    if (Rejected.class.isInstance(remoteState)) {
                        final Rejected rejected = (Rejected) remoteState;
                        if (rejected.getError() == null) {
                            LOG.debug("message [message ID: {}] rejected by peer", messageId);
                            e = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST);
                        } else {
                            LOG.debug("message [message ID: {}] rejected by peer: {}, {}", messageId,
                                    rejected.getError().getCondition(), rejected.getError().getDescription());
                            e = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, rejected.getError().getDescription());
                        }
                    } else {
                        LOG.debug("message [message ID: {}] not accepted by peer: {}", messageId, remoteState);
                        e = new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST);
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
            currentSpan.finish();
            return Future.failedFuture(t);
        });
    }
}
