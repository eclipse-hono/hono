/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp.impl;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.device.amqp.CommandResponder;
import org.eclipse.hono.client.device.amqp.TraceableCommandResponder;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;

/**
 * A Vertx-Proton based client for sending response messages to commands to Hono's AMQP adapter.
 */
public final class AmqpAdapterClientCommandResponseSender extends AbstractAmqpAdapterClientSender
        implements CommandResponder, TraceableCommandResponder {

    private AmqpAdapterClientCommandResponseSender(
            final HonoConnection connection,
            final GenericSenderLink senderLink,
            final String tenantId) {
        super(connection, senderLink, tenantId);
    }

    /**
     * Creates a new sender to send responses for commands back to the business application.
     * <p>
     * Note that the given connection has to be connected and the invocation has to be done
     * on the vert.x context the connection was created in.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the events will be published for.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if con or tenantId is {@code null}.
     */
    public static Future<TraceableCommandResponder> create(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);

        return GenericSenderLink.create(con, remoteCloseHook)
                .map(sender -> new AmqpAdapterClientCommandResponseSender(con, sender, tenantId));
    }

    @Override
    public Future<ProtonDelivery> sendCommandResponse(
            final String deviceId,
            final String targetAddress,
            final String correlationId,
            final int status,
            final Buffer payload,
            final String contentType,
            final Map<String, Object> properties) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(correlationId);

        return sendCommandResponse(deviceId, targetAddress, correlationId, status, payload, contentType,
                properties, NoopSpan.INSTANCE);
    }

    @Override
    public Future<ProtonDelivery> sendCommandResponse(
            final String deviceId,
            final String targetAddress,
            final String correlationId,
            final int status,
            final Buffer payload,
            final String contentType,
            final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(correlationId);

        final Span span = createSpan(deviceId, "send command response", context);
        TracingHelper.TAG_CORRELATION_ID.set(span, correlationId);
        return sendCommandResponse(deviceId, targetAddress, correlationId, status, payload, contentType,
                properties, span);
    }

    private Future<ProtonDelivery> sendCommandResponse(
            final String deviceId,
            final String targetAddress,
            final String correlationId,
            final int status,
            final Buffer payload,
            final String contentType,
            final Map<String, Object> properties,
            final Span span) {

        final Message message = createMessage(deviceId, payload, contentType, properties, targetAddress);
        message.setCorrelationId(correlationId);
        MessageHelper.addStatus(message, status);
        return sendAndWaitForOutcome(message, span);
    }
}
