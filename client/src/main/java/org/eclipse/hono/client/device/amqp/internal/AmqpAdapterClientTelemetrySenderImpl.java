/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp.internal;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.device.amqp.TelemetrySender;
import org.eclipse.hono.client.device.amqp.TraceableTelemetrySender;
import org.eclipse.hono.client.impl.TelemetrySenderImpl;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for publishing telemetry messages to Hono's AMQP adapter.
 */
public class AmqpAdapterClientTelemetrySenderImpl extends TelemetrySenderImpl
        implements TelemetrySender, TraceableTelemetrySender {

    AmqpAdapterClientTelemetrySenderImpl(final HonoConnection con, final ProtonSender sender, final String tenantId,
            final String targetAddress) {
        super(con, sender, tenantId, targetAddress);
    }

    /**
     * Creates a new sender for publishing telemetry data to Hono's AMQP adapter.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the telemetry data will be published for.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if con or tenantId is {@code null}.
     */
    public static Future<TelemetrySender> createWithAnonymousLinkAddress(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);

        final String targetAddress = null; // anonymous relay
        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .map(sender -> new AmqpAdapterClientTelemetrySenderImpl(con, sender, tenantId, targetAddress));
    }

    /**
     * Shadowing the static factory method, which only works within a protocol adapter, a use case that is not supported
     * by this class.
     *
     * @param con not used.
     * @param tenantId not used.
     * @param remoteCloseHook not used.
     * @return nothing.
     * @throws UnsupportedOperationException always.
     */
    public static Future<DownstreamSender> create(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> remoteCloseHook) {
        throw new UnsupportedOperationException("This method is not supported by this class");
    }

    @Override
    public Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, ?> properties) {
        return send(deviceId, payload, contentType, properties, null);
    }

    @Override
    public Future<ProtonDelivery> send(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, ?> properties, final SpanContext context) {
        final Message msg = createMessage(deviceId, payload, contentType, properties);
        return send(msg, context);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final String deviceId, final byte[] payload,
            final String contentType, final Map<String, ?> properties) {
        return sendAndWaitForOutcome(deviceId, payload, contentType, properties, null);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final String deviceId, final byte[] payload,
            final String contentType, final Map<String, ?> properties, final SpanContext context) {
        final Message msg = createMessage(deviceId, payload, contentType, properties);
        return sendAndWaitForOutcome(msg, context);
    }

    private Message createMessage(final String deviceId, final byte[] payload, final String contentType,
            final Map<String, ?> properties) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(contentType);

        final Message msg = ProtonHelper.message();
        msg.setAddress(getTo(deviceId));
        MessageHelper.setPayload(msg, contentType, payload);
        setApplicationProperties(msg, properties);
        MessageHelper.addDeviceId(msg, deviceId);
        return msg;
    }
}
