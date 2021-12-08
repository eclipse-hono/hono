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

package org.eclipse.hono.client.telemetry.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedTelemetrySender}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedTelemetrySenderTest {

    private final RegistrationAssertion device = new RegistrationAssertion("the-device");
    private final Vertx vertxMock = mock(Vertx.class);

    private MessagingKafkaProducerConfigProperties kafkaProducerConfig;
    private TenantObject tenant;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        tenant = new TenantObject("the-tenant", true);
        kafkaProducerConfig = new MessagingKafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(new HashMap<>());

    }

    /**
     * Verifies that the Kafka record is created as expected when sending telemetry data.
     *
     * @param qos The quality of service used for sending the message.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @CsvSource(value = { "AT_MOST_ONCE", "AT_LEAST_ONCE" })
    public void testSendTelemetryCreatesCorrectRecord(
            final QoS qos,
            final VertxTestContext ctx) {

        // GIVEN a telemetry sender
        final String payload = "the-payload";
        final String contentType = "text/plain";
        final Map<String, Object> properties = Map.of("foo", "bar");
        final var spanFinished = ctx.checkpoint();
        final var messageHasHeaders = ctx.checkpoint();
        final var span = TracingMockSupport.mockSpan();
        doAnswer(invocation -> {
            spanFinished.flag();
            return null;
        }).when(span).finish();
        final var tracer = TracingMockSupport.mockTracer(span);

        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final var sender = new KafkaBasedTelemetrySender(factory, kafkaProducerConfig, true, tracer);

        // WHEN sending telemetry data
        sender.sendTelemetry(tenant, device, qos, contentType, Buffer.buffer(payload), properties, null)
            .onComplete(ctx.succeeding(t -> {
                ctx.verify(() -> {

                    // THEN the producer record is created from the given values...
                    final var producerRecord = mockProducer.history().get(0);

                    assertThat(producerRecord.key()).isEqualTo(device.getDeviceId());
                    assertThat(producerRecord.topic())
                            .isEqualTo(new HonoTopic(HonoTopic.Type.TELEMETRY, tenant.getTenantId()).toString());
                    assertThat(producerRecord.value().toString()).isEqualTo(payload);

                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(producerRecord.headers(), "foo", "bar");

                    // ...AND contains the standard headers
                    KafkaClientUnitTestHelper.assertStandardHeaders(
                            producerRecord,
                            device.getDeviceId(),
                            contentType,
                            qos.ordinal());

                });
                messageHasHeaders.flag();
            }));
    }

    /**
     * Verifies that the constructor throws an NPE if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final Tracer tracer = NoopTracerFactory.create();

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(null, kafkaProducerConfig, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(factory, null, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedTelemetrySender(factory, kafkaProducerConfig, true, null));
    }

    /**
     * Verifies that
     * {@link KafkaBasedTelemetrySender#sendTelemetry(TenantObject, RegistrationAssertion, QoS, String, Buffer, Map, io.opentracing.SpanContext)}
     * throws an NPE if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendTelemetryThrowsOnMissingMandatoryParameter() {
        final QoS qos = QoS.AT_LEAST_ONCE;
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final Tracer tracer = NoopTracerFactory.create();

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
