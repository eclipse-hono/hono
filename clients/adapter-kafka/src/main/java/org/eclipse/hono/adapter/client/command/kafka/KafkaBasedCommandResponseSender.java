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
package org.eclipse.hono.adapter.client.command.kafka;

import java.util.List;
import java.util.Objects;

import org.eclipse.hono.adapter.client.command.CommandResponse;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;

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

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, response.getTenantId());
        log.trace("send command [response topic : {}, tenant-id: {}, device-id: {}, correlation-id: {}",
                topic, response.getTenantId(), response.getDeviceId(), response.getCorrelationId());

        return sendAndWaitForOutcome(topic.toString(), response.getTenantId(), response.getDeviceId(),
                response.getPayload(), getHeaders(response), context);
    }

    private List<KafkaHeader> getHeaders(final CommandResponse response) {
        return List.of(KafkaRecordHelper.createKafkaHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID,
                response.getCorrelationId()),
                KafkaRecordHelper.createKafkaHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, response.getDeviceId()),
                KafkaRecordHelper.createKafkaHeader(MessageHelper.APP_PROPERTY_STATUS, response.getStatus()),
                KafkaRecordHelper.createKafkaHeader(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                        Objects.nonNull(response.getContentType()) ? response.getContentType()
                                : MessageHelper.CONTENT_TYPE_OCTET_STREAM));
    }
}
