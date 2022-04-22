/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.command.kafka;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.KafkaProducerHelper;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.util.DownstreamMessageProperties;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A Kafka based client for sending command response messages downstream via a Kafka cluster.
 *
 * @see "https://www.eclipse.org/hono/docs/api/command-and-control-kafka/"
 */
public class KafkaBasedCommandResponseSender extends AbstractKafkaBasedMessageSender<Buffer> implements CommandResponseSender {

    /**
     * Creates a new Kafka-based command response sender.
     *
     * @param vertx The vert.x instance to use.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandResponseSender(
            final Vertx vertx,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, CommandConstants.COMMAND_RESPONSE_ENDPOINT, producerConfig, tracer);

        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())) {
                        producerFactory.getProducer(CommandConstants.COMMAND_RESPONSE_ENDPOINT)
                                .ifPresent(producer -> removeTenantTopicBasedProducerMetrics(producer, notification.getTenantId()));
                    }
                });
    }

    private void removeTenantTopicBasedProducerMetrics(final KafkaProducer<String, Buffer> producer, final String tenantId) {
        final HonoTopic topic = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId);
        KafkaProducerHelper.removeTopicMetrics(producer, Stream.of(topic.toString()));
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

        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, response.getTenantId()).toString();
        final Span span = startChildSpan(
                "forward Command response",
                topic,
                response.getTenantId(),
                response.getDeviceId(),
                context);
        if (response.getMessagingType() != getMessagingType()) {
            span.log(String.format("using messaging type %s instead of type %s used for the original command",
                    getMessagingType(), response.getMessagingType()));
        }

        return sendAndWaitForOutcome(
                    topic,
                    response.getTenantId(),
                    response.getDeviceId(),
                    response.getPayload(),
                    getHeaders(response, tenant, device),
                    span)
                .onComplete(ar -> span.finish());
    }

    private Map<String, Object> getHeaders(
            final CommandResponse response,
            final TenantObject tenant,
            final RegistrationAssertion device) {

        final var headers = new DownstreamMessageProperties(
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                tenant.getDefaults().getMap(),
                device.getDefaults(),
                response.getAdditionalProperties(),
                tenant.getResourceLimits())
            .asMap();

        headers.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, response.getCorrelationId());
        headers.put(MessageHelper.APP_PROPERTY_TENANT_ID, response.getTenantId());
        headers.put(MessageHelper.APP_PROPERTY_DEVICE_ID, response.getDeviceId());
        headers.put(MessageHelper.APP_PROPERTY_STATUS, response.getStatus());

        if (response.getContentType() != null) {
            headers.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, response.getContentType());
        } else if (response.getPayload() != null) {
            headers.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM);
        }
        return headers;
    }
}
