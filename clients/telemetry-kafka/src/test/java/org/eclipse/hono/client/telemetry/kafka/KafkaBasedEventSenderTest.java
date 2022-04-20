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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.producer.CachingKafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link KafkaBasedEventSender}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedEventSenderTest {

    private TenantObject tenant = new TenantObject("the-tenant", true);
    private final RegistrationAssertion device = new RegistrationAssertion("the-device");

    private Vertx vertxMock;
    private MessagingKafkaProducerConfigProperties kafkaProducerConfig;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        vertxMock = mock(Vertx.class);
        when(vertxMock.eventBus()).thenReturn(mock(EventBus.class));
        kafkaProducerConfig = new MessagingKafkaProducerConfigProperties();
        kafkaProducerConfig.setProducerConfig(Map.of("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092"));
    }

    private CachingKafkaProducerFactory<String, Buffer> newProducerFactory(
            final MockProducer<String, Buffer> mockProducer) {

        return CachingKafkaProducerFactory.testFactory(vertxMock,
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    /**
     * Verifies that the Kafka record is created as expected.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendEventCreatesCorrectRecord(final VertxTestContext ctx) {

        // GIVEN a sender
        final String contentType = "text/plain";
        final String payload = "the-payload";
        final Map<String, Object> properties = Map.of(
                "foo", "bar",
                MessageHelper.SYS_HEADER_PROPERTY_TTL, 5);

        final var span = TracingMockSupport.mockSpan();
        final var tracer = TracingMockSupport.mockTracer(span);

        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = newProducerFactory(mockProducer);
        final Promise<Void> readyTracker = Promise.promise();
        final var sender = new KafkaBasedEventSender(vertxMock, factory, kafkaProducerConfig, true, tracer);
        sender.addOnKafkaProducerReadyHandler(readyTracker);

        // WHEN sending a message
        sender.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> sender.sendEvent(tenant, device, contentType, Buffer.buffer(payload), properties, null))
            .onComplete(ctx.succeeding(t -> {
                ctx.verify(() -> {
                    // THEN the producer record is created from the given values...
                    final var producerRecord = mockProducer.history().get(0);

                    assertThat(producerRecord.key()).isEqualTo(device.getDeviceId());
                    assertThat(producerRecord.topic())
                            .isEqualTo(new HonoTopic(HonoTopic.Type.EVENT, tenant.getTenantId()).toString());
                    assertThat(producerRecord.value().toString()).isEqualTo(payload);

                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(producerRecord.headers(), "foo", "bar");
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            producerRecord.headers(),
                            MessageHelper.SYS_HEADER_PROPERTY_TTL,
                            5000L);

                    // ...AND contains the standard headers
                    KafkaClientUnitTestHelper.assertStandardHeaders(
                            producerRecord,
                            device.getDeviceId(),
                            contentType,
                            QoS.AT_LEAST_ONCE.ordinal());

                    verify(span).finish();
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final Tracer tracer = NoopTracerFactory.create();

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedEventSender(null, factory, kafkaProducerConfig, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedEventSender(vertxMock, null, kafkaProducerConfig, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedEventSender(vertxMock, factory, null, true, tracer));

        assertThrows(NullPointerException.class,
                () -> new KafkaBasedEventSender(vertxMock, factory, kafkaProducerConfig, true, null));
    }

    /**
     * Verifies that
     * {@link KafkaBasedEventSender#sendEvent(TenantObject, RegistrationAssertion, String, Buffer, Map, io.opentracing.SpanContext)}
     * throws a nullpointer exception if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendEventThrowsOnMissingMandatoryParameter() {
        final MockProducer<String, Buffer> mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
        final Tracer tracer = NoopTracerFactory.create();

        final KafkaBasedEventSender sender = new KafkaBasedEventSender(vertxMock, factory, kafkaProducerConfig, true, tracer);

        assertThrows(NullPointerException.class,
                () -> sender.sendEvent(null, device, "the-content-type", null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.sendEvent(tenant, null, "the-content-type", null, null, null));

    }
}
