/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.telemetry.kafka;

import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A client for publishing event messages to a Kafka cluster.
 */
public class KafkaBasedEventSender extends AbstractKafkaBasedDownstreamSender implements EventSender {

    /**
     * Creates a new Kafka-based event sender.
     *
     * @param vertx The vert.x instance to use.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @param includeDefaults {@code true} if a device's default properties should be included in messages being sent.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedEventSender(
            final Vertx vertx,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties kafkaProducerConfig,
            final boolean includeDefaults,
            final Tracer tracer) {

        super(vertx, producerFactory, EventConstants.EVENT_ENDPOINT, kafkaProducerConfig, includeDefaults, tracer);
    }

    @Override
    protected HonoTopic.Type getTopicType() {
        return HonoTopic.Type.EVENT;
    }

    @Override
    public Future<Void> sendEvent(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);

        if (log.isTraceEnabled()) {
            log.trace("sending event [tenantId: {}, deviceId: {}, contentType: {}, properties: {}]",
                    tenant.getTenantId(), device.getDeviceId(), contentType, properties);
        }

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.EVENT, tenant.getTenantId());
        final Map<String, Object> propsWithDefaults = addDefaults(
                topic.getType().endpoint,
                tenant,
                device,
                QoS.AT_LEAST_ONCE,
                contentType,
                payload,
                properties);
        final String topicName = topic.toString();

        final Span currentSpan = startChildSpan(
                "forward Event",
                topicName,
                tenant.getTenantId(),
                device.getDeviceId(),
                context);
        return sendAndWaitForOutcome(
                topic.toString(),
                tenant.getTenantId(),
                device.getDeviceId(),
                payload,
                propsWithDefaults,
                currentSpan)
            .onComplete(ar -> currentSpan.finish());
    }

    @Override
    public String toString() {
        return KafkaBasedEventSender.class.getName() + " via Kafka";
    }
}
