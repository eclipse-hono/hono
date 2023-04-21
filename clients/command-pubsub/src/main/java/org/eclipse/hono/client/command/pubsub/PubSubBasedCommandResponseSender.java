/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.command.pubsub;

import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.pubsub.AbstractPubSubBasedMessageSender;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.client.util.DownstreamMessageProperties;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A Pub/Sub based client for sending command response messages downstream via Google Pub/Sub.
 */
public class PubSubBasedCommandResponseSender extends AbstractPubSubBasedMessageSender implements
        CommandResponseSender {

    /**
     * Creates a new Pub/Sub based command response sender.
     *
     * @param vertx The vert.x instance to use.
     * @param publisherFactory The factory to use for creating Pub/Sub publishers.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PubSubBasedCommandResponseSender(final Vertx vertx, final PubSubPublisherFactory publisherFactory,
            final String projectId, final Tracer tracer) {
        super(publisherFactory, CommandConstants.COMMAND_RESPONSE_ENDPOINT, projectId, tracer);
        Objects.requireNonNull(vertx);

        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())) {
                        publisherFactory
                                .getPublisher(CommandConstants.COMMAND_RESPONSE_ENDPOINT, notification.getTenantId())
                                .ifPresent(publisher -> publisherFactory.closePublisher(
                                        CommandConstants.COMMAND_RESPONSE_ENDPOINT,
                                        notification.getTenantId()));
                    }
                });
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

        if (log.isTraceEnabled()) {
            log.trace("publish command response [{}]", response);
        }
        final Span span = startSpan(
                "forward Command response",
                response.getTenantId(),
                response.getDeviceId(),
                References.CHILD_OF,
                context);
        if (response.getMessagingType() != getMessagingType()) {
            span.log(String.format("using messaging type %s instead of type %s used for the original command",
                    getMessagingType(), response.getMessagingType()));
        }
        final Map<String, Object> properties = getMessageProperties(response, tenant, device);
        final String topic = PubSubMessageHelper.getTopicName(CommandConstants.COMMAND_RESPONSE_ENDPOINT,
                response.getTenantId());
        return sendAndWaitForOutcome(
                topic,
                response.getTenantId(),
                response.getDeviceId(),
                response.getPayload(),
                properties,
                span)
                .onComplete(ar -> span.finish());
    }

    private Map<String, Object> getMessageProperties(final CommandResponse response, final TenantObject tenant,
            final RegistrationAssertion device) {
        final var messageProperties = new DownstreamMessageProperties(
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                tenant.getDefaults().getMap(),
                device.getDefaults(),
                response.getAdditionalProperties(),
                tenant.getResourceLimits()).asMap();
        messageProperties.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, response.getCorrelationId());
        messageProperties.put(MessageHelper.APP_PROPERTY_STATUS, response.getStatus());
        messageProperties.put(MessageHelper.APP_PROPERTY_TENANT_ID, response.getTenantId());
        messageProperties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, response.getDeviceId());

        if (response.getContentType() != null) {
            messageProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, response.getContentType());
        } else if (response.getPayload() != null) {
            messageProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM);
        }
        return messageProperties;
    }
}
