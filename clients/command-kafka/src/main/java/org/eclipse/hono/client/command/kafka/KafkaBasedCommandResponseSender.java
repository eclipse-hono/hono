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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;

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
            final KafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, CommandConstants.COMMAND_RESPONSE_ENDPOINT, producerConfig, tracer);
    }

    @Override
    public Future<Void> sendCommandResponse(final CommandResponse response, final SpanContext context) {

        Objects.requireNonNull(response);

        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, response.getTenantId()).toString();
        final Span span = startChildSpan("forward Command response", topic, response.getTenantId(),
                response.getDeviceId(), context);
        log.trace("publish command response [{}]", response);
        if (response.getMessagingType() != getMessagingType()) {
            span.log(String.format("using messaging type %s instead of type %s used for the original command",
                    getMessagingType(), response.getMessagingType()));
        }
        return sendAndWaitForOutcome(topic, response.getTenantId(), response.getDeviceId(), response.getPayload(),
                getHeaders(response, span), span);
    }

    private List<KafkaHeader> getHeaders(final CommandResponse response, final Span span) {

        final List<KafkaHeader> headers = new ArrayList<>(encodePropertiesAsKafkaHeaders(response.getAdditionalProperties(), span));
        headers.add(KafkaRecordHelper.createKafkaHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID, response.getCorrelationId()));
        headers.add(KafkaRecordHelper.createKafkaHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, response.getDeviceId()));
        headers.add(KafkaRecordHelper.createKafkaHeader(MessageHelper.APP_PROPERTY_STATUS, response.getStatus()));
        headers.add(KafkaRecordHelper.createKafkaHeader(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                Objects.nonNull(response.getContentType()) ? response.getContentType()
                        : MessageHelper.CONTENT_TYPE_OCTET_STREAM));
        return headers;
    }
}
