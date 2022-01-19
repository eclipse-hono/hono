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
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * A client for publishing telemetry messages to a Kafka cluster.
 */
public class KafkaBasedTelemetrySender extends AbstractKafkaBasedDownstreamSender implements TelemetrySender {

    /**
     * Creates a new Kafka-based telemetry sender.
     *
     * @param vertx The vert.x instance to use.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @param includeDefaults {@code true} if a device's default properties should be included in messages being sent.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedTelemetrySender(
            final Vertx vertx,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties kafkaProducerConfig,
            final boolean includeDefaults,
            final Tracer tracer) {

        super(vertx, producerFactory, TelemetryConstants.TELEMETRY_ENDPOINT, kafkaProducerConfig, includeDefaults, tracer);
    }

    @Override
    protected HonoTopic.Type getTopicType() {
        return HonoTopic.Type.TELEMETRY;
    }

    @Override
    public Future<Void> sendTelemetry(
            final TenantObject tenant,
            final RegistrationAssertion device,
            final QoS qos,
            final String contentType,
            final Buffer payload,
            final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        if (log.isTraceEnabled()) {
            log.trace("send telemetry data [tenantId: {}, deviceId: {}, qos: {}, contentType: {}, properties: {}]",
                    tenant.getTenantId(), device.getDeviceId(), qos, contentType, properties);
        }

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.TELEMETRY, tenant.getTenantId());
        final Map<String, Object> propsWithDefaults = addDefaults(
                topic.getType().endpoint,
                tenant,
                device,
                qos,
                contentType,
                payload,
                properties);
        final String topicName = topic.toString();

        final Span currentSpan = startSpan(
                "forward Telemetry",
                topicName,
                tenant.getTenantId(),
                device.getDeviceId(),
                qos == QoS.AT_MOST_ONCE ? References.FOLLOWS_FROM : References.CHILD_OF,
                context);
        final var outcome = sendAndWaitForOutcome(
                topic.toString(),
                tenant.getTenantId(),
                device.getDeviceId(),
                payload,
                propsWithDefaults,
                currentSpan)
            .onComplete(ar -> currentSpan.finish());

        if (qos == QoS.AT_MOST_ONCE) {
            return Future.succeededFuture();
        } else {
            return outcome;
        }
    }

    @Override
    public String toString() {
        return KafkaBasedTelemetrySender.class.getName() + " via Kafka";
    }
}
