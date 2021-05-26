/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.telemetry.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.hono.client.kafka.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedTelemetrySender}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedTelemetrySenderTest {

    private final TenantObject tenant = new TenantObject("the-tenant", true);
    private final RegistrationAssertion device = new RegistrationAssertion("the-device");
    private final Tracer tracer = NoopTracerFactory.create();

    private KafkaProducerConfigProperties kafkaProducerConfig;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        kafkaProducerConfig = new KafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(new HashMap<>());

    }

    /**
     * Verifies that the Kafka record is created as expected when sending telemetry data with QoS 0.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendTelemetryCreatesCorrectRecordWithQoS0(final VertxTestContext ctx) {

        // GIVEN a telemetry sender
        final QoS qos = QoS.AT_MOST_ONCE;
        final String payload = "the-payload";
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = new CachingKafkaProducerFactory<>(
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final KafkaBasedTelemetrySender sender = new KafkaBasedTelemetrySender(factory, kafkaProducerConfig,
                true, tracer);

        // WHEN sending telemetry data with QoS 0
        sender.sendTelemetry(tenant, device, qos, "the-content-type", Buffer.buffer(payload), null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the producer record is created from the given values...
                        final ProducerRecord<String, Buffer> actual = mockProducer.history().get(0);

                        assertThat(actual.key()).isEqualTo(device.getDeviceId());
                        assertThat(actual.topic())
                                .isEqualTo(new HonoTopic(HonoTopic.Type.TELEMETRY, tenant.getTenantId()).toString());
                        assertThat(actual.value().toString()).isEqualTo(payload);

                        // ...AND contains the standard headers
                        KafkaClientUnitTestHelper
                                .assertStandardHeaders(actual, device.getDeviceId(), "the-content-type", qos.ordinal());
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies that the Kafka record is created as expected when sending telemetry data with QoS 1.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendTelemetryCreatesCorrectRecordWithQoS1(final VertxTestContext ctx) {

        // GIVEN a telemetry sender
        final QoS qos = QoS.AT_LEAST_ONCE;
        final String contentType = "the-content-type";
        final String payload = "the-payload";
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = new CachingKafkaProducerFactory<>(
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final KafkaBasedTelemetrySender sender = new KafkaBasedTelemetrySender(factory, kafkaProducerConfig,
                true, tracer);

        // WHEN sending telemetry data with QoS 1
        sender.sendTelemetry(tenant, device, qos, contentType, Buffer.buffer(payload), null, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the producer record is created from the given values...
                        final ProducerRecord<String, Buffer> actual = mockProducer.history().get(0);

                        assertThat(actual.key()).isEqualTo(device.getDeviceId());
                        assertThat(actual.topic())
                                .isEqualTo(new HonoTopic(HonoTopic.Type.TELEMETRY, tenant.getTenantId()).toString());
                        assertThat(actual.value().toString()).isEqualTo(payload);

                        // ...AND contains the standard headers
                        KafkaClientUnitTestHelper.assertStandardHeaders(actual, device.getDeviceId(), contentType, qos.ordinal());
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final CachingKafkaProducerFactory<String, Buffer> factory = new CachingKafkaProducerFactory<>(
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(KafkaClientUnitTestHelper.newMockProducer(true)));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(null, kafkaProducerConfig, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(factory, null, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(factory, kafkaProducerConfig, true, null));
    }

    /**
     * Verifies that
     * {@link KafkaBasedTelemetrySender#sendTelemetry(TenantObject, RegistrationAssertion, QoS, String, Buffer, Map, SpanContext)}
     * throws a nullpointer exception if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendTelemetryThrowsOnMissingMandatoryParameter() {
        final QoS qos = QoS.AT_LEAST_ONCE;
        final CachingKafkaProducerFactory<String, Buffer> factory = new CachingKafkaProducerFactory<>(
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(KafkaClientUnitTestHelper.newMockProducer(true)));
        final KafkaBasedTelemetrySender sender = new KafkaBasedTelemetrySender(factory, kafkaProducerConfig,
                true, tracer);

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(null, device, qos, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(tenant, null, qos, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendTelemetry(tenant, device, null, "the-content-type", null, null, null));

    }
}
