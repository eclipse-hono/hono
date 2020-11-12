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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.config.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.client.CachingKafkaProducerFactory;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.client.test.TestHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;

/**
 * Verifies behavior of {@link KafkaBasedTelemetrySender}.
 */
public class KafkaBasedTelemetrySenderTest {

    private final RegistrationAssertion device = new RegistrationAssertion("the-device");
    private final TenantObject tenant = new TenantObject("the-tenant", true);
    private final Tracer tracer = NoopTracerFactory.create();

    private CachingKafkaProducerFactory<String, Buffer> factory;
    private KafkaProducerConfigProperties kafkaProducerConfig;
    private KafkaBasedTelemetrySender sender;

    /**
     * Sets up the telemetry sender.
     */
    @BeforeEach
    public void setUp() {

        kafkaProducerConfig = new KafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(new HashMap<>());

        factory = CachingKafkaProducerFactory.testProducerFactory();
        sender = new KafkaBasedTelemetrySender(factory, kafkaProducerConfig, tracer);
    }

    /**
     * Verifies that the Kafka record is created as expected when sending telemetry data with QoS 0.
     */
    @Test
    public void testSendTelemetryCreatesCorrectRecordWithQoS0() {

        // GIVEN a telemetry sender
        final QoS qos = QoS.AT_MOST_ONCE;
        final String payload = "the-payload";

        // WHEN sending telemetry data with QoS 0
        sender.sendTelemetry(tenant, device, qos, "the-content-type", Buffer.buffer(payload), null, null);

        // THEN the producer record is created from the given values...
        final ProducerRecord<String, Buffer> actual = TestHelper.getUnderlyingMockProducer(factory, "telemetry")
                .history().get(0);

        assertThat(actual.key()).isEqualTo(device.getDeviceId());
        assertThat(actual.topic()).isEqualTo(new HonoTopic(HonoTopic.Type.TELEMETRY, tenant.getTenantId()).toString());
        assertThat(actual.value().toString()).isEqualTo(payload);

        // ...AND contains the standard headers
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("content-type", "the-content-type".getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("device_id", device.getDeviceId().getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("qos", Json.encode(qos.ordinal()).getBytes()));

    }

    /**
     * Verifies that the Kafka record is created as expected when sending telemetry data with QoS 1.
     */
    @Test
    public void testSendTelemetryCreatesCorrectRecordWithQoS1() {

        // GIVEN a telemetry sender
        final QoS qos = QoS.AT_LEAST_ONCE;
        final String payload = "the-payload";

        // WHEN sending telemetry data with QoS 1
        sender.sendTelemetry(tenant, device, qos, "the-content-type", Buffer.buffer(payload), null, null);

        // THEN the producer record is created from the given values...
        final ProducerRecord<String, Buffer> actual = TestHelper.getUnderlyingMockProducer(factory, "telemetry")
                .history().get(0);

        assertThat(actual.key()).isEqualTo(device.getDeviceId());
        assertThat(actual.topic()).isEqualTo(new HonoTopic(HonoTopic.Type.TELEMETRY, tenant.getTenantId()).toString());
        assertThat(actual.value().toString()).isEqualTo(payload);

        // ...AND contains the standard headers
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("content-type", "the-content-type".getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("device_id", device.getDeviceId().getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("qos", Json.encode(qos.ordinal()).getBytes()));

    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(null, kafkaProducerConfig, tracer));

        assertThrows(NullPointerException.class, () -> new KafkaBasedTelemetrySender(factory, null, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(factory, kafkaProducerConfig, null));
    }

    /**
     * Verifies that
     * {@link KafkaBasedTelemetrySender#sendTelemetry(TenantObject, RegistrationAssertion, QoS, String, Buffer, Map, SpanContext)}
     * throws a nullpointer exception if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendTelemetryThrowsOnMissingMandatoryParameter() {
        final QoS qos = QoS.AT_LEAST_ONCE;

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(null, device, qos, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(tenant, null, qos, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(tenant, device, null, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(tenant, device, qos, null, null, null, null));

    }
}
