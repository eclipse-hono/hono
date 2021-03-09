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
package org.eclipse.hono.application.client.kafka.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.application.client.CommandSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A Kafka based client for sending commands.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">
 *      Command &amp; Control API for Kafka Specification</a>
 */
public class KafkaBasedCommandSender extends AbstractKafkaBasedMessageSender implements CommandSender {

    /**
     * Creates a new Kafka-based command sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param config          The Kafka producer configuration properties to use.
     * @param tracer          The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandSender(
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final KafkaProducerConfigProperties config,
            final Tracer tracer) {
        super(producerFactory, "command-sender", config, tracer);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The replyId is not used in the Kafka based implementation. It can be set to {@code null}.
     * If set it will be ignored.
     *
     * @throws NullPointerException if tenantId, deviceId, command or correlationId is {@code null}.
     */
    @Override
    public Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final String correlationId,
            final String replyId,
            final Map<String, Object> properties,
            final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(correlationId);

        return sendCommand(tenantId, deviceId, command, contentType, data, correlationId, properties, true, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final Map<String, Object> properties,
            final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        return sendCommand(tenantId, deviceId, command, contentType, data, null, properties, false, context);
    }

    private Future<Void> sendCommand(final String tenantId, final String deviceId, final String command,
            final String contentType, final Buffer data, final String correlationId,
            final Map<String, Object> properties, final boolean responseRequired, final SpanContext context) {

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId);
        final Map<String, Object> headerProperties = getHeaderProperties(deviceId, command, contentType, correlationId,
                responseRequired, properties);

        return sendAndWaitForOutcome(topic.toString(), tenantId, deviceId, data, headerProperties, context);
    }

    private Map<String, Object> getHeaderProperties(final String deviceId, final String subject,
            final String contentType, final String correlationId, final boolean responseRequired,
            final Map<String, Object> properties) {

        final Map<String, Object> props = Optional.ofNullable(properties)
                .map(HashMap::new)
                .orElseGet(HashMap::new);

        props.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        props.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject);
        props.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                Objects.nonNull(contentType) ? contentType : MessageHelper.CONTENT_TYPE_OCTET_STREAM);
        Optional.ofNullable(correlationId)
                .ifPresent(id -> props.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, id));
        props.put(KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, responseRequired);

        return props;
    }
}
