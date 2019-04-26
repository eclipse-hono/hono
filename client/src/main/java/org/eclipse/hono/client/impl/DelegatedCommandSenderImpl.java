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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
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
        return CommandConstants.COMMAND_ENDPOINT;
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
                // TODO improve error-handling: we have to use a different kind of sendMessageAndWaitForOutcome() here
                //  where a non-Accepted updatedProtonDelivery.getRemoteState() does not fail the returned Future
                //  (we want to transfer such errors as is, not by way of translating them to/from ServiceInvocationExceptions)
                sendMessageAndWaitForOutcome(rawMessage, span).setHandler(result);
            }
        });
    }

    @Override
    public Future<ProtonDelivery> sendCommandMessage(final Command command, final SpanContext spanContext) {
        Objects.requireNonNull(command);
        final String tenantId = command.getTenant();
        final String deviceId = command.getDeviceId();
        final String targetAddress = getTargetAddress(tenantId, deviceId);
        final String replyToAddress = command.isOneWay() ? null
                : String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, tenantId, command.getReplyToId());
        return sendAndWaitForOutcome(
                createDelegatedCommandMessage(command.getCommandMessage(), targetAddress, replyToAddress),
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
        return String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, Objects.requireNonNull(tenantId),
                Objects.requireNonNull(deviceId));
    }

    private static Message createDelegatedCommandMessage(final Message originalMessage, final String targetAddress,
            final String replyToAddress) {
        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(originalMessage);
        // copy original message
        final Message msg = MessageHelper.getShallowCopy(originalMessage);
        // set target address
        msg.setAddress(targetAddress);
        msg.setReplyTo(replyToAddress);
        // use original message id as correlation id
        msg.setCorrelationId(originalMessage.getMessageId());
        return msg;
    }

    /**
     * Creates a new sender for sending the delegated command messages to the AMQP network.
     *
     * @param con The connection to the AMQP network.
     * @param closeHook A handler to invoke if the peer closes the link unexpectedly.
     * @return A future indicating the result of the creation attempt.
     * @throws NullPointerException if con is {@code null}.
     */
    public static Future<DelegatedCommandSender> create(
            final HonoConnection con,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);

        final String targetAddress = ""; // use anonymous relay (ie. use empty address)
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook)
                .map(sender -> (DelegatedCommandSender) new DelegatedCommandSenderImpl(con, sender));
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
