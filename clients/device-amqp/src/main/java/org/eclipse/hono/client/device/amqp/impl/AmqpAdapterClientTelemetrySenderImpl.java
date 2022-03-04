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
import org.eclipse.hono.client.device.amqp.TelemetrySender;
import org.eclipse.hono.client.device.amqp.TraceableTelemetrySender;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.TelemetryConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;

/**
 * A Vertx-Proton based client for publishing telemetry messages to Hono's AMQP adapter.
 */
public final class AmqpAdapterClientTelemetrySenderImpl extends AbstractAmqpAdapterClientSender
        implements TelemetrySender, TraceableTelemetrySender {

    AmqpAdapterClientTelemetrySenderImpl(final HonoConnection connection, final GenericSenderLink senderLink,
    final String tenantId) {
        super(connection, senderLink, tenantId);
    }

    /**
     * Creates a new sender for publishing telemetry data to Hono's AMQP adapter.
     * <p>
     * Note that the given connection has to be connected and the invocation has to be done
     * on the vert.x context the connection was created in.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the telemetry data will be published for.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if con or tenantId is {@code null}.
     */
    public static Future<TelemetrySender> create(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);

        return GenericSenderLink.create(con, remoteCloseHook)
                .map(sender -> new AmqpAdapterClientTelemetrySenderImpl(con, sender, tenantId));
    }

    @Override
    public Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, Object> properties) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);

        final Message msg = createMessage(deviceId, payload, contentType, properties);
        return send(msg, NoopSpan.INSTANCE);
    }

    @Override
    public Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, Object> properties, final SpanContext context) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);

        final Span span = createSpan(deviceId, "send telemetry message", context);
        final Message msg = createMessage(deviceId, payload, contentType, properties);
        return send(msg, span);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final String deviceId, final byte[] payload,
            final String contentType, final Map<String, Object> properties) {

        final Message msg = createMessage(deviceId, payload, contentType, properties);
        return sendAndWaitForOutcome(msg, NoopSpan.INSTANCE);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final String deviceId, final byte[] payload,
            final String contentType, final Map<String, Object> properties, final SpanContext context) {

        final Span span = createSpan(deviceId, "send telemetry message", context);
        final Message msg = createMessage(deviceId, payload, contentType, properties);
        return sendAndWaitForOutcome(msg, span);
    }

    private Message createMessage(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, Object> properties) {
        final String targetAddress = AddressHelper.getTargetAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, deviceId, null);
        return createMessage(deviceId, payload, contentType, properties, targetAddress);
    }
}
