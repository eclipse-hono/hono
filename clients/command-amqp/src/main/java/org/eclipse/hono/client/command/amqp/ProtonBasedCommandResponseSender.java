/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.command.amqp;

import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.amqp.AbstractServiceClient;
import org.eclipse.hono.client.amqp.DownstreamAmqpMessageFactory;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.amqp.config.AddressHelper;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.util.DownstreamMessageProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.proton.ProtonHelper;


/**
 * A vertx-proton based client for sending command response messages downstream via the AMQP 1.0 Messaging Network.
 *
 */
public class ProtonBasedCommandResponseSender extends AbstractServiceClient implements CommandResponseSender {

    private final boolean jmsVendorPropsEnabled;

    /**
     * Creates a new sender for a connection.
     *
     * @param connection The connection to the AMQP 1.0 Messaging Network.
     * @param samplerFactory The factory for creating samplers for tracing AMQP messages being sent.
     * @param jmsVendorPropsEnabled {@code true} if JMS vendor props should be included in downstream messages.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public ProtonBasedCommandResponseSender(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final boolean jmsVendorPropsEnabled) {
        super(connection, samplerFactory);
        this.jmsVendorPropsEnabled = jmsVendorPropsEnabled;
    }

    private Future<GenericSenderLink> createSender(final String tenantId, final String replyId) {
        return connection.executeOnContext(result -> {
            GenericSenderLink.create(
                    connection,
                    CommandConstants.COMMAND_RESPONSE_ENDPOINT,
                    tenantId,
                    replyId,
                    samplerFactory.create(CommandConstants.COMMAND_RESPONSE_ENDPOINT),
                    onRemoteClose -> {})
            .onComplete(result);
        });
    }

    private Message createDownstreamMessage(
            final CommandResponse response,
            final TenantObject tenant,
            final RegistrationAssertion device,
            final Map<String, Object> properties) {

        final var props = new DownstreamMessageProperties(
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                tenant.getDefaults().getMap(),
                device.getDefaults(),
                properties,
                tenant.getResourceLimits())
            .asMap();

        final Message msg = ProtonHelper.message();
        DownstreamAmqpMessageFactory.addDefaults(msg, props);
        MessageHelper.setCreationTime(msg);
        msg.setCorrelationId(response.getCorrelationId());
        MessageHelper.setPayload(msg, response.getContentType(), response.getPayload());
        MessageHelper.addStatus(msg, response.getStatus());
        msg.setAddress(AddressHelper.getTargetAddress(
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                response.getTenantId(),
                response.getReplyToId(),
                null));
        MessageHelper.addTenantId(msg, response.getTenantId());
        MessageHelper.addDeviceId(msg, response.getDeviceId());
        if (jmsVendorPropsEnabled) {
            DownstreamAmqpMessageFactory.addJmsVendorProperties(msg);
        }
        return msg;
    }

    @Override
    public Future<Void> sendCommandResponse(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final CommandResponse response,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(response);

        final var sender = createSender(response.getTenantId(), response.getReplyToId());

        return sender
                .recover(thr -> Future.failedFuture(StatusCodeMapper.toServerError(thr)))
                .compose(s -> {
                    final Message msg = createDownstreamMessage(response, tenant, device, response.getAdditionalProperties());
                    final Span span = newChildSpan(context, "forward Command response");
                    if (response.getMessagingType() != getMessagingType()) {
                        span.log(String.format("using messaging type %s instead of type %s used for the original command",
                                getMessagingType(), response.getMessagingType()));
                    }
                    return s.sendAndWaitForOutcome(msg, span);
                })
                .onSuccess(delivery -> sender.result().close())
                .mapEmpty();
    }
}
