/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.application.client.amqp;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.CommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.amqp.SenderCachingServiceClient;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonHelper;

/**
 * A vertx-proton based client for sending commands.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control/">
 *      Command &amp; Control API for AMQP 1.0 Specification</a>
 */
public class ProtonBasedCommandSender extends SenderCachingServiceClient implements CommandSender {

    /**
     * Creates a new vertx-proton based command sender.
     *
     * @param connection The connection to the Hono server.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @throws NullPointerException if any of the parameters except the closeHandler is {@code null}.
     */
    public ProtonBasedCommandSender(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if tenantId, deviceId, command, correlationId or replyId is {@code null}.
     */
    @Override
    public Future<Void> sendAsyncCommand(final String tenantId, final String deviceId, final String command,
            final String contentType, final Buffer data, final String correlationId, final String replyId,
            final Map<String, Object> properties, final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(replyId);

        return getOrCreateSenderLink(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId)
                .recover(thr -> Future.failedFuture(StatusCodeMapper.toServerError(thr)))
                .compose(sender -> {
                    final String targetAddress = AddressHelper
                            .getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, deviceId,
                                    connection.getConfig());
                    final Message msg = createMessage(tenantId, deviceId, command, contentType, data, correlationId,
                            replyId, targetAddress, properties);
                    return sender.sendAndWaitForOutcome(msg, newChildSpan(context, "send command " + command));
                })
                .mapEmpty();
    }

    private static Message createMessage(final String tenantId, final String deviceId, final String command,
            final String contentType, final Buffer data, final String correlationId, final String replyId,
            final String targetAddress, final Map<String, Object> properties) {
        final Message msg = ProtonHelper.message();

        MessageHelper.setCreationTime(msg);
        msg.setAddress(targetAddress);
        msg.setReplyTo(
                String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, replyId));
        MessageHelper.setApplicationProperties(msg, properties);
        msg.setCorrelationId(correlationId);
        msg.setSubject(command);
        MessageHelper.setPayload(msg, contentType, data);
        MessageHelper.addTenantId(msg, tenantId);
        MessageHelper.addDeviceId(msg, deviceId);

        return msg;
    }
}
