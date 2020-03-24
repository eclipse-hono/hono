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
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.device.amqp.CommandResponder;
import org.eclipse.hono.client.device.amqp.TraceableCommandResponder;
import org.eclipse.hono.client.impl.CommandResponseSenderImpl;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A Vertx-Proton based client for sending response messages to commands to Hono's AMQP adapter.
 */
public class AmqpAdapterClientCommandResponseSender extends CommandResponseSenderImpl
        implements CommandResponder, TraceableCommandResponder {

    AmqpAdapterClientCommandResponseSender(final HonoConnection connection, final ProtonSender sender,
            final String tenantId) {
        super(connection, sender, tenantId, null);
    }

    /**
     * Creates a new sender to send responses for commands back to the business application.
     *
     * @param con The connection to the Hono server.
     * @param tenantId The tenant that the events will be published for.
     * @param remoteCloseHook The handler to invoke when the link is closed by the peer (may be {@code null}). The
     *            sender's target address is provided as an argument to the handler.
     * @return A future indicating the outcome.
     * @throws NullPointerException if con or tenantId is {@code null}.
     */
    public static Future<CommandResponder> createWithAnonymousLinkAddress(
            final HonoConnection con,
            final String tenantId,
            final Handler<String> remoteCloseHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);

        return con.createSender(null, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook)
                .map(sender -> new AmqpAdapterClientCommandResponseSender(con, sender, tenantId));
    }

    /**
     * Shadowing the static factory method, which only works within a protocol adapter, a use case that is not supported
     * by this class.
     *
     * @param con not used.
     * @param tenantId not used.
     * @param replyId not used.
     * @param closeHook not used.
     * @return nothing.
     * @throws UnsupportedOperationException always.
     */
    public static Future<CommandResponseSender> create(
            final HonoConnection con,
            final String tenantId,
            final String replyId,
            final Handler<String> closeHook) {
        throw new UnsupportedOperationException("This method is not supported by this class");
    }

    @Override
    public Future<ProtonDelivery> sendCommandResponse(final String deviceId, final String targetAddress,
            final String correlationId, final int status, final byte[] payload, final String contentType,
            final Map<String, ?> properties) {
        return sendCommandResponse(deviceId, targetAddress, correlationId, status, payload, contentType,
                properties, null);
    }

    @Override
    public Future<ProtonDelivery> sendCommandResponse(final String deviceId, final String targetAddress,
            final String correlationId, final int status, final byte[] payload,
            final String contentType, final Map<String, ?> properties, final SpanContext context) {

        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(correlationId);

        final Message message = ProtonHelper.message();
        message.setAddress(targetAddress);
        message.setCorrelationId(correlationId);

        MessageHelper.setCreationTime(message);

        setApplicationProperties(message, properties);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        MessageHelper.addProperty(message, MessageHelper.APP_PROPERTY_STATUS, status);
        MessageHelper.setPayload(message, contentType, payload);

        return sendAndWaitForOutcome(message, context);
    }

    /**
     * The method in the parent class is only intended for use by a protocol adapter to forward the command response
     * downstream, and this use case is not supported in this class.
     * 
     * @param commandResponse not used.
     * @param context not used.
     * @return nothing.
     * @throws UnsupportedOperationException always.
     */
    @Override
    public Future<ProtonDelivery> sendCommandResponse(final CommandResponse commandResponse,
            final SpanContext context) {
        throw new UnsupportedOperationException("This method is not supported by this class");
    }
}
