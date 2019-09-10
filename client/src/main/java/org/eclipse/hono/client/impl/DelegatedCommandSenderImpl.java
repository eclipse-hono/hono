/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.HonoConnection;
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
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based sender to send command messages to the downstream peer as part of delegating them to be
 * processed by another command consumer.
 */
public class DelegatedCommandSenderImpl extends AbstractSender implements DelegatedCommandSender {

    DelegatedCommandSenderImpl(
            final HonoConnection connection,
            final ProtonSender sender) {

        super(connection, sender, "", "");
    }

    @Override
    protected String getTo(final String deviceId) {
        return null;
    }

    @Override
    public String getEndpoint() {
        return CommandConstants.NORTHBOUND_COMMAND_LEGACY_ENDPOINT;
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
        span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, MessageHelper.getTenantId(rawMessage));
        span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, MessageHelper.getDeviceId(rawMessage));
        TracingHelper.injectSpanContext(connection.getTracer(), span.context(), rawMessage);

        return runSendAndWaitForOutcomeOnContext(rawMessage, span);
    }

    private Future<ProtonDelivery> runSendAndWaitForOutcomeOnContext(final Message rawMessage, final Span span) {
        return connection.executeOrRunOnContext(result -> {
            if (sender.sendQueueFull()) {
                final ServiceInvocationException e = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "no credit available");
                logError(span, e);
                span.finish();
                result.fail(e);
            } else {
                sendMessageAndWaitForOutcome(rawMessage, span).setHandler(result);
            }
        });
    }

    /**
     * Sends an AMQP 1.0 message to the peer this client is configured for and waits for the outcome of the transfer.
     * <p>
     * This method overrides the base implementation to also return a succeeded Future if a delivery update other than
     * <em>Accepted</em> was received.
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

        final Future<ProtonDelivery> result = Future.future();
        final String messageId = String.format("%s-%d", getClass().getSimpleName(), MESSAGE_COUNTER.getAndIncrement());
        message.setMessageId(messageId);
        logMessageIdAndSenderInfo(currentSpan, messageId);

        final Long timerId = connection.getConfig().getSendMessageTimeout() > 0
                ? connection.getVertx().setTimer(connection.getConfig().getSendMessageTimeout(), id -> {
                    if (!result.isComplete()) {
                        final ServerErrorException exception = new ServerErrorException(
                                HttpURLConnection.HTTP_UNAVAILABLE,
                                "waiting for delivery update timed out after "
                                        + connection.getConfig().getSendMessageTimeout() + "ms");
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
                result.complete(deliveryUpdated);
            } else {
                LOG.debug("peer did not settle message [message ID: {}, remote state: {}], failing delivery",
                        messageId, remoteState);
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

    @Override
    public Future<ProtonDelivery> sendCommandMessage(final Command command, final SpanContext spanContext) {
        Objects.requireNonNull(command);
        final String replyToAddress = command.isOneWay() ? null
                : String.format("%s/%s/%s", command.getReplyToEndpoint(), command.getTenant(), command.getReplyToId());
        return sendAndWaitForOutcome(createDelegatedCommandMessage(command.getCommandMessage(), replyToAddress),
                spanContext);
    }

    /**
     * Gets the AMQP <em>target</em> address to use for sending the delegated command messages to.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @return The target address.
     * @throws NullPointerException if tenant or device id is {@code null}.
     */
    static String getTargetAddress(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_LEGACY_ENDPOINT, Objects.requireNonNull(tenantId),
                Objects.requireNonNull(deviceId));
    }

    private static Message createDelegatedCommandMessage(final Message originalMessage, final String replyToAddress) {
        Objects.requireNonNull(originalMessage);
        // copy original message
        final Message msg = MessageHelper.getShallowCopy(originalMessage);
        msg.setReplyTo(replyToAddress);
        // use original message id as correlation id
        msg.setCorrelationId(originalMessage.getMessageId());
        return msg;
    }

    /**
     * Creates a new sender for sending the delegated command messages to the AMQP network.
     *
     * @param con The connection to the AMQP network.
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param closeHook A handler to invoke if the peer closes the link unexpectedly (may be {@code null}).
     * @return A future indicating the result of the creation attempt.
     * @throws NullPointerException if con is {@code null}.
     */
    public static Future<DelegatedCommandSender> create(
            final HonoConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);

        final String targetAddress = getTargetAddress(tenantId, deviceId);
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook)
                .map(sender -> new DelegatedCommandSenderImpl(con, sender));
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        if (connection.getTracer() == null) {
            throw new IllegalStateException("no tracer configured");
        } else {
            final Span span = newChildSpan(parent, "delegate Command request");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            return span;
        }
    }
}
