/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.telemetry.kafka;

import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.config.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.client.CachingKafkaProducerFactory;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A client for publishing telemetry messages to a Kafka cluster.
 */
public class KafkaBasedTelemetrySender extends AbstractKafkaBasedDownstreamSender implements TelemetrySender {

    /**
     * Creates a new Kafka-based telemetry sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedTelemetrySender(final CachingKafkaProducerFactory<String, Buffer> producerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig, final Tracer tracer) {

        super(producerFactory,
                TelemetryConstants.TELEMETRY_ENDPOINT,
                Objects.requireNonNull(kafkaProducerConfig).getAtLeastOnceConfig(),
                tracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendTelemetry(final TenantObject tenant, final RegistrationAssertion device, final QoS qos,
            final String contentType, final Buffer payload, final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(qos);

        final String tenantId = tenant.getTenantId();
        final String deviceId = device.getDeviceId();

        log.trace("send telemetry data [tenantId: {}, deviceId: {}, qos: {}, contentType: {}, properties: {}]",
                tenantId, deviceId, qos, contentType, properties);

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.TELEMETRY, tenantId);
        return send(topic, tenantId, deviceId, qos, contentType, payload, properties, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return KafkaBasedTelemetrySender.class.getName() + " via Kafka";
    }

}
