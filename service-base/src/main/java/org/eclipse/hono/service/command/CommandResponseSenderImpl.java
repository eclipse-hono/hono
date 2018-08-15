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
package org.eclipse.hono.service.command;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.AbstractSender;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * The response sender for a received command.
 */
public class CommandResponseSenderImpl extends AbstractSender implements CommandResponseSender {

    /**
     * The default amount of time to wait for credits after link creation. This
     * is higher as in the client defaults, because for the command response the link
     * is created on demand and the response should not fail.
     */
    public static final long DEFAULT_COMMAND_FLOW_LATENCY = 200L; //ms

    CommandResponseSenderImpl(
            final ClientConfigProperties config,
            final ProtonSender sender,
            final String tenantId,
            final String targetAddress,
            final Context context,
            final Tracer tracer) {

        super(config, sender, tenantId, targetAddress, context, tracer);
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
        return CommandConstants.COMMAND_ENDPOINT;
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {
        return send(message);
    }

    @Override
    public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext context) {
        return send(message, context);
    }

    static final String getTargetAddress(final String tenantId, final String replyId) {
        return String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId, replyId);
    }

    /**
     * {@inheritDoc}
     */
    public Future<ProtonDelivery> sendCommandResponse(
            final String correlationId,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final int status,
            final SpanContext context) {

        LOG.trace("sending command response [correlationId: {}, status: {}]", correlationId, status);
        return sendAndWaitForOutcome(
                createResponseMessage(targetAddress, correlationId, contentType, payload, properties, status),
                context);
    }

    /**
     * {@inheritDoc}
     */
    public Future<ProtonDelivery> sendCommandResponse(final CommandResponse commandResponse, final SpanContext context) {

        Objects.requireNonNull(commandResponse);
        return sendAndWaitForOutcome(createResponseMessage(commandResponse), context);
    }

    private Message createResponseMessage(final CommandResponse commandResponse) {

        return createResponseMessage(
                targetAddress,
                commandResponse.getCorrelationId(),
                commandResponse.getContentType(),
                commandResponse.getPayload(),
                null,
                commandResponse.getStatus());
    }

    private static Message createResponseMessage(
            final String targetAddress,
            final String correlationId,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final int status) {

        Objects.requireNonNull(targetAddress);
        Objects.requireNonNull(correlationId);
        final Message msg = ProtonHelper.message();
        msg.setCorrelationId(correlationId);
        msg.setAddress(targetAddress);
        if (contentType != null) {
            msg.setContentType(contentType);
        }
        if (payload != null) {
            msg.setBody(new Data(new Binary(payload.getBytes())));
        }
        if (properties != null) {
            msg.setApplicationProperties(new ApplicationProperties(properties));
        }
        MessageHelper.setCreationTime(msg);
        MessageHelper.addProperty(msg, MessageHelper.APP_PROPERTY_STATUS, status);
        return msg;
    }

    /**
     * Creates a new sender to send responses for commands back to the business application.
     * <p>
     * The underlying sender link will be created with the following properties:
     * <ul>
     * <li><em>flow latency</em> will be set to @{@link #DEFAULT_COMMAND_FLOW_LATENCY} if
     * the configured value is smaller than the default</li>
     * </ul>
     *
     * @param context The vert.x context to run all interactions with the server on.
     * @param clientConfig The configuration properties to use.
     * @param con The connection to the AMQP network.
     * @param tenantId The tenant that the command response will be send for and the device belongs to.
     * @param replyId The reply id as the unique postfix of the replyTo address.
     * @param closeHook A handler to invoke if the peer closes the link unexpectedly.
     * @param creationHandler The handler to invoke with the result of the creation attempt.
     * @param tracer The tracer to use for tracking the processing of received
     *               messages. If {@code null}, OpenTracing's {@code NoopTracer} will be used.
     * @throws NullPointerException if any of context, clientConfig, con, tenantId, deviceId or replyId  are {@code null}.
     */
    public static void create(
            final Context context,
            final ClientConfigProperties clientConfig,
            final ProtonConnection con,
            final String tenantId,
            final String replyId,
            final Handler<String> closeHook,
            final Handler<AsyncResult<CommandResponseSender>> creationHandler,
            final Tracer tracer) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(clientConfig);
        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);

        final String targetAddress = CommandResponseSenderImpl.getTargetAddress(tenantId, replyId);
        final ClientConfigProperties props = new ClientConfigProperties(clientConfig);
        if (props.getFlowLatency() < DEFAULT_COMMAND_FLOW_LATENCY) {
            props.setFlowLatency(DEFAULT_COMMAND_FLOW_LATENCY);
        }

        createSender(context, props, con, targetAddress, ProtonQoS.AT_LEAST_ONCE, closeHook)
            .map(sender -> (CommandResponseSender) new CommandResponseSenderImpl(clientConfig, sender, tenantId, targetAddress, context, tracer))
            .setHandler(creationHandler);
    }

    @Override
    protected Span startSpan(final SpanContext parent, final Message rawMessage) {

        if (tracer == null) {
            throw new IllegalStateException("no tracer configured");
        } else {
            final Span span = newChildSpan(parent, "forward Command response");
            Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
            return span;
        }
    }
}
