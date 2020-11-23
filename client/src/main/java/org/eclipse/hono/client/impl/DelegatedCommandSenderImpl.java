/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based sender to send command messages to the downstream peer as part of delegating them to be
 * processed by another command consumer.
 */
public class DelegatedCommandSenderImpl extends AbstractSender implements DelegatedCommandSender {

    DelegatedCommandSenderImpl(
            final HonoConnection connection,
            final ProtonSender sender,
            final SendMessageSampler sampler) {

        super(connection, sender, "", "", sampler);
    }

    @Override
    protected String getTo(final String deviceId) {
        return null;
    }

    @Override
    public String getEndpoint() {
        return CommandConstants.INTERNAL_COMMAND_ENDPOINT;
    }

    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {
        return runSendAndWaitForOutcomeOnContext(message, currentSpan);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {
        return sendAndWaitForOutcome(message, null);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message rawMessage, final SpanContext parent) {
        Objects.requireNonNull(rawMessage);

        final Span span = startSpan(parent, rawMessage);
        TracingHelper.injectSpanContext(connection.getTracer(), span.context(), rawMessage);

        return runSendAndWaitForOutcomeOnContext(rawMessage, span);
    }

    private Future<ProtonDelivery> runSendAndWaitForOutcomeOnContext(final Message rawMessage, final Span span) {
        return connection.executeOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServiceInvocationException e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available");
                logMessageSendingError("error sending message [ID: {}, address: {}], no credit available (drain={})",
                        rawMessage.getMessageId(), getMessageAddress(rawMessage), sender.getDrain());
                TracingHelper.TAG_CREDIT.set(span, 0);
                logError(span, e);
                span.finish();
                result.fail(e);
            } else {
                sendMessageAndWaitForOutcome(rawMessage, span).onComplete(result);
            }
        });
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for and waits for the outcome of the transfer.
     * <p>
     * This method overrides the base implementation to also return a succeeded Future if a delivery update other than
     * <em>Accepted</em> was received. And in contrast to the base implementation it doesn't set a new message id on
     * the given message.
     *
     * @param message The message to send.
     * @param currentSpan The <em>OpenTracing</em> span used to trace the sending of the message.
     *              The span will be finished by this method and will contain an error log if
     *              the message has not been accepted by the peer.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been accepted (and settled)
     *         by the consumer.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} if the
     *         message could not be sent or if no delivery update
     *         was received from the peer within the configured timeout period
     *         (see {@link ClientConfigProperties#getSendMessageTimeout()}).
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    @Override
    protected Future<ProtonDelivery> sendMessageAndWaitForOutcome(final Message message, final Span currentSpan) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(currentSpan);

        final AtomicReference<ProtonDelivery> deliveryRef = new AtomicReference<>();
        final Promise<ProtonDelivery> result = Promise.promise();
        final String messageId = message.getMessageId() != null ? message.getMessageId().toString() : "";
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final SendMessageSampler.Sample sample = this.sampler.start(this.tenantId);

        final Long timerId = connection.getConfig().getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(connection.getConfig().getSendMessageTimeout(), id -> {
                    if (!result.future().isComplete()) {
                        final ServerErrorException exception = new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE,
                                "waiting for delivery update timed out after "
                                        + connection.getConfig().getSendMessageTimeout() + "ms");
                        logMessageSendingError("waiting for delivery update timed out for message [ID: {}, address: {}] after {}ms",
                                messageId, getMessageAddress(message), connection.getConfig().getSendMessageTimeout());
                        // settle and release the delivery - this ensures that the message isn't considered "in flight"
                        // anymore in the AMQP messaging network and that it doesn't count towards the link capacity
                        // (it would be enough to just settle the delivery without an outcome but that cannot be done with proton-j as of now)
                        Optional.ofNullable(deliveryRef.get())
                                .ifPresent(delivery -> ProtonHelper.released(delivery, true));
                        result.fail(exception);
                        sample.timeout();
                    }
                })
                : null;

        deliveryRef.set(sender.send(message, deliveryUpdated -> {
            if (timerId != null) {
                connection.getVertx().cancelTimer(timerId);
            }
            final DeliveryState remoteState = deliveryUpdated.getRemoteState();
            sample.completed(remoteState);
            if (result.future().isComplete()) {
                log.debug("ignoring received delivery update for message [ID: {}, address: {}]: waiting for the update has already timed out",
                        messageId, getMessageAddress(message));
            } else if (deliveryUpdated.remotelySettled()) {
                logUpdatedDeliveryState(currentSpan, message, deliveryUpdated);
                result.complete(deliveryUpdated);
            } else {
                logMessageSendingError("peer did not settle message [ID: {}, address: {}, remote state: {}], failing delivery",
                        messageId, getMessageAddress(message), remoteState.getClass().getSimpleName());
                final ServiceInvocationException e = new ServerErrorException(
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        "peer did not settle message, failing delivery");
                result.fail(e);
            }
        }));
        log.trace("sent message [ID: {}, address: {}], remaining credit: {}, queued messages: {}", messageId,
                getMessageAddress(message), sender.getCredit(), sender.getQueued());

        return result.future()
                .map(delivery -> {
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

    @Override
    public Future<ProtonDelivery> sendCommandMessage(final Command command, final SpanContext spanContext) {
        Objects.requireNonNull(command);
        final String replyToAddress = command.isOneWay() ? null
                : String.format("%s/%s/%s", command.getReplyToEndpoint(), command.getTenant(), command.getReplyToId());
        final Message delegatedCommandMessage = createDelegatedCommandMessage(command.getCommandMessage(), replyToAddress);
        final Span span = startSpan(spanContext, delegatedCommandMessage);
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, command.getTenant());
        if (command.isTargetedAtGateway()) {
            MessageHelper.addProperty(delegatedCommandMessage, MessageHelper.APP_PROPERTY_CMD_VIA, command.getDeviceId());
            TracingHelper.TAG_DEVICE_ID.set(span, command.getOriginalDeviceId());
            TracingHelper.TAG_GATEWAY_ID.set(span, command.getDeviceId());
        } else {
            TracingHelper.TAG_DEVICE_ID.set(span, command.getDeviceId());
        }
        TracingHelper.injectSpanContext(connection.getTracer(), span.context(), delegatedCommandMessage);

        return runSendAndWaitForOutcomeOnContext(delegatedCommandMessage, span);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending the delegated command messages to.
     *
     * @param adapterInstanceId The protocol adapter instance id.
     * @return The target address.
     * @throws NullPointerException if adapterInstanceId is {@code null}.
     */
    static String getTargetAddress(final String adapterInstanceId) {
        return CommandConstants.INTERNAL_COMMAND_ENDPOINT + "/" + Objects.requireNonNull(adapterInstanceId);
    }

    private static Message createDelegatedCommandMessage(final Message originalMessage, final String replyToAddress) {
        Objects.requireNonNull(originalMessage);
        // copy original message
        final Message msg = MessageHelper.getShallowCopy(originalMessage);
        msg.setReplyTo(replyToAddress);
        return msg;
    }

    /**
     * Creates a new sender for sending the delegated command messages to the AMQP network.
     *
     * @param con The connection to the AMQP network.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param sampler The sampler to use.
     * @param remoteCloseHook A handler to invoke if the peer closes the link unexpectedly (may be {@code null}).
     * @return A future indicating the result of the creation attempt.
     * @throws NullPointerException if any of the parameters except remoteCloseHook is {@code null}.
     */
    public static Future<DelegatedCommandSender> create(
            final HonoConnection con,
            final String adapterInstanceId,
            final SendMessageSampler sampler,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(adapterInstanceId);
        Objects.requireNonNull(sampler);

        final String targetAddress = getTargetAddress(adapterInstanceId);
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .map(sender -> new DelegatedCommandSenderImpl(con, sender, sampler));
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        final Span span = newChildSpan(parent, "delegate Command request");
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        return span;
    }
}
