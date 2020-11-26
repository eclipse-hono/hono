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

import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.client.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.client.KafkaProducerFactory;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * A client for publishing event messages to a Kafka cluster.
 */
public class KafkaBasedEventSender extends AbstractKafkaBasedMessageSender implements EventSender {

    /**
     * Creates a new Kafka-based event sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedEventSender(final KafkaProducerFactory<String, Buffer> producerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig, final Tracer tracer) {

        super(producerFactory, EventConstants.EVENT_ENDPOINT, kafkaProducerConfig.getProducerConfig(), tracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendEvent(final TenantObject tenant, final RegistrationAssertion device,
            final String contentType, final Buffer payload, final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(contentType);

        final String tenantId = tenant.getTenantId();
        final String deviceId = device.getDeviceId();

        log.trace("sending event [tenantId: {}, deviceId: {}, contentType: {}, properties: {}]", tenantId, deviceId,
                contentType, properties);

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.EVENT, tenantId);
        return send(topic, tenantId, deviceId, QoS.AT_LEAST_ONCE, contentType, payload, properties, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return KafkaBasedEventSender.class.getName() + " via Kafka";
    }

}
