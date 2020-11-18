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
 * Verifies behavior of {@link KafkaBasedEventSender}.
 */
public class KafkaBasedEventSenderTest {

    private final RegistrationAssertion device = new RegistrationAssertion("the-device");
    private final TenantObject tenant = new TenantObject("the-tenant", true);
    private final Tracer tracer = NoopTracerFactory.create();

    private CachingKafkaProducerFactory<String, Buffer> factory;
    private KafkaProducerConfigProperties kafkaProducerConfig;
    private KafkaBasedEventSender sender;

    /**
     * Sets up the event sender.
     */
    @BeforeEach
    public void setUp() {

        kafkaProducerConfig = new KafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(new HashMap<>());

        factory = CachingKafkaProducerFactory.testProducerFactory();
        sender = new KafkaBasedEventSender(factory, kafkaProducerConfig, tracer);

    }

    /**
     * Verifies that the Kafka record is created as expected.
     */
    @Test
    public void testSendEventCreatesCorrectRecord() {

        // GIVEN a sender
        final String payload = "the-payload";

        // WHEN sending a message
        sender.sendEvent(tenant, device, "the-content-type", Buffer.buffer(payload), null, null);

        // THEN the producer record is created from the given values...
        final ProducerRecord<String, Buffer> actual = TestHelper.getUnderlyingMockProducer(factory, "event")
                .history().get(0);

        assertThat(actual.key()).isEqualTo(device.getDeviceId());
        assertThat(actual.topic()).isEqualTo(new HonoTopic(HonoTopic.Type.EVENT, tenant.getTenantId()).toString());
        assertThat(actual.value().toString()).isEqualTo(payload);

        // ...AND contains the standard headers
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("content-type", "the-content-type".getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("device_id", device.getDeviceId().getBytes()));
        assertThat(actual.headers())
                .containsOnlyOnce(new RecordHeader("qos", Json.encode(QoS.AT_LEAST_ONCE.ordinal()).getBytes()));

    }


    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        assertThrows(NullPointerException.class, () -> new KafkaBasedEventSender(null, kafkaProducerConfig, tracer));
        assertThrows(NullPointerException.class, () -> new KafkaBasedEventSender(factory, null, tracer));
        assertThrows(NullPointerException.class, () -> new KafkaBasedEventSender(factory, kafkaProducerConfig, null));
    }

    /**
     * Verifies that
     * {@link KafkaBasedEventSender#sendEvent(TenantObject, RegistrationAssertion, String, Buffer, Map, SpanContext)}
     * throws a nullpointer exception if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendEventThrowsOnMissingMandatoryParameter() {

        assertThrows(NullPointerException.class,
                () -> sender.sendEvent(null, device, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendEvent(tenant, null, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendEvent(tenant, device, null, null, null, null));

    }
}
