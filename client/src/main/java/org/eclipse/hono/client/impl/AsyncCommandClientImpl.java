/**
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
 */

package org.eclipse.hono.client.impl;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.AsyncCommandClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Sender for commands with asynchronous responses.
 */
public class AsyncCommandClientImpl extends AbstractSender implements AsyncCommandClient {

    private AsyncCommandClientImpl(
            final HonoConnection con,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress) {
        super(con, sender, tenantId, targetAddress);
    }

    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {
        return sendMessageAndWaitForOutcome(message, currentSpan);
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message message) {
        if (connection.getTracer() == null) {
            throw new IllegalStateException("no tracer configured");
        } else {
            final Span span = newFollowingSpan(parent, "sending async command");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
            return span;
        }
    }

    @Override
    protected String getTo(final String deviceId) {
        return null;
    }

    @Override
    public String getEndpoint() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    static String getTargetAddress(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {
        return send(message);
    }

    @Override
    public Future<Void> sendAsyncCommand(final String command, final Buffer data, final String correlationId,
            final String replyId) {
        return sendAsyncCommand(command, null, data, correlationId, replyId, null);
    }

    @Override
    public Future<Void> sendAsyncCommand(final String command, final String contentType, final Buffer data,
            final String correlationId, final String replyId, final Map<String, Object> properties) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(replyId);

        final Message message = ProtonHelper.message();
        message.setCorrelationId(correlationId);
        MessageHelper.setCreationTime(message);
        MessageHelper.setPayload(message, contentType, data);
        message.setSubject(command);
        final String replyToAddress = String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId, replyId);
        message.setReplyTo(replyToAddress);

        return sendAndWaitForOutcome(message).compose(ignore -> Future.succeededFuture());
    }

    /**
     * Creates a new asynchronous command client for a tenant and device.
     * <p>
     * The instance created is scoped to the given device. In particular, the sender link's target address is set to
     * <em>control/${tenantId}/${deviceId}</em>.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device to create the client for.
     * @param closeHook A handler to invoke if the peer closes the sender link unexpectedly.
     * @return A future indicating the outcome.
     * @throws NullPointerException if any of connection, tenantId or deviceId are {@code null}.
     */
    public static Future<AsyncCommandClient> create(
            final HonoConnection con,
            final String tenantId,
            final String deviceId,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final String targetAddress = AsyncCommandClientImpl.getTargetAddress(tenantId, deviceId);
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook)
                .compose(sender -> Future.succeededFuture(new AsyncCommandClientImpl(con, sender, tenantId, targetAddress)));
    }
}
