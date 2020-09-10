/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.CommandConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A wrapper around an AMQP link for sending response messages to
 * commands downstream.
 */
public class CommandResponseSenderImpl extends AbstractSender implements CommandResponseSender {

    /**
     * The default amount of time to wait for credits after link creation. This is higher as in the client defaults,
     * because for the command response the link is created on demand and the response should not fail.
     */
    public static final long DEFAULT_COMMAND_FLOW_LATENCY = 200L; // ms

    /**
     * Creates a command response sender instance for a given connection and proton sender.
     *
     * @param connection The open connection to the Hono server.
     * @param sender The sender link to send command response messages over.
     * @param tenantId The tenant that the messages will be published for.
     * @param targetAddress The target address to send the messages to.
     * @param sampler The sampler to use.
     */
    protected CommandResponseSenderImpl(
            final HonoConnection connection,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress,
            final SendMessageSampler sampler) {

        super(connection, sender, tenantId, targetAddress, sampler);
    }

    @Override
    protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {
        return sendMessageAndWaitForOutcome(message, currentSpan);
    }

    @Override
    protected String getTo(final String deviceId) {
        return null;
    }

    @Override
    public String getEndpoint() {
        return CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT;
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {
        return send(message);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext context) {
        return send(message, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<ProtonDelivery> sendCommandResponse(final CommandResponse commandResponse,
            final SpanContext context) {

        Objects.requireNonNull(commandResponse);
        final Message message = commandResponse.toMessage();
        Objects.requireNonNull(message);
        message.setAddress(AddressHelper.getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, commandResponse.getReplyToId(), null));
        return sendAndWaitForOutcome(message, context);
    }

    /**
     * Creates a new sender to send responses for commands back to the business application.
     * <p>
     * The underlying sender link will be created with the following properties:
     * <ul>
     * <li><em>flow latency</em> will be set to @{@link #DEFAULT_COMMAND_FLOW_LATENCY} if the configured value is
     * smaller than the default</li>
     * </ul>
     *
     * @param con The connection to the AMQP network.
     * @param tenantId The tenant that the command response will be send for and the device belongs to.
     * @param replyId The reply id as the unique postfix of the replyTo address.
     * @param sampler The sampler to use.
     * @param closeHook A handler to invoke if the peer closes the link unexpectedly.
     * @return A future indicating the result of the creation attempt.
     * @throws NullPointerException if any of con, tenantId or replyId are {@code null}.
     */
    public static Future<CommandResponseSender> create(
            final HonoConnection con,
            final String tenantId,
            final String replyId,
            final SendMessageSampler sampler,
            final Handler<String> closeHook) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);

        final String targetAddress = AddressHelper.getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, replyId, con.getConfig());
        final ClientConfigProperties props = new ClientConfigProperties(con.getConfig());
        if (props.getFlowLatency() < DEFAULT_COMMAND_FLOW_LATENCY) {
            props.setFlowLatency(DEFAULT_COMMAND_FLOW_LATENCY);
        }

        return con.createSender(targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook)
                .map(sender -> (CommandResponseSender) new CommandResponseSenderImpl(con, sender, tenantId,
                        targetAddress, sampler));
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        final Span span = newChildSpan(parent, "forward Command response");
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
        return span;
    }
}
