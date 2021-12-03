/**
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
package org.eclipse.hono.client.command.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A Kafka based client for sending command response messages downstream via a Kafka cluster.
 *
 * @see "https://www.eclipse.org/hono/docs/api/command-and-control-kafka/"
 */
public class KafkaBasedCommandResponseSender extends AbstractKafkaBasedMessageSender implements CommandResponseSender {

    /**
     * Creates a new Kafka-based command response sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandResponseSender(
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, CommandConstants.COMMAND_RESPONSE_ENDPOINT, producerConfig, tracer);
    }

    @Override
    public Future<Void> sendCommandResponse(
            final CommandResponse response,
            final SpanContext context) {

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
                getHeaders(response),
                span)
            .onComplete(ar -> span.finish());
    }

    private Map<String, Object> getHeaders(final CommandResponse response) {
        final Map<String, Object> headers = new HashMap<>(response.getAdditionalProperties());
        headers.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, response.getCorrelationId());
        headers.put(MessageHelper.APP_PROPERTY_TENANT_ID, response.getTenantId());
        headers.put(MessageHelper.APP_PROPERTY_DEVICE_ID, response.getDeviceId());
        headers.put(MessageHelper.APP_PROPERTY_STATUS, response.getStatus());
        if (response.getPayload() != null) {
            headers.put(
                    MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                    Optional.ofNullable(response.getContentType()).orElse(MessageHelper.CONTENT_TYPE_OCTET_STREAM));
        }
        return headers;
    }
}
