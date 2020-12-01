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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.kafka.client.CachingKafkaProducerFactory;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.util.QoS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link AbstractKafkaBasedDownstreamSender}.
 */
@ExtendWith(VertxExtension.class)
public class AbstractKafkaBasedDownstreamSenderTest {

    private static final String TENANT_ID = "the-tenant";
    private static final String DEVICE_ID = "the-device";
    private static final QoS qos = QoS.AT_LEAST_ONCE;
    private static final String CONTENT_TYPE = "the-content-type";
    private static final String PRODUCER_NAME = "test-producer";

    protected final Tracer tracer = NoopTracerFactory.create();
    private final Map<String, String> config = Collections.singletonMap("hono.kafka.producerConfig.bootstrap.servers",
            "localhost:9092");
    private final HonoTopic topic = new HonoTopic(HonoTopic.Type.EVENT, TENANT_ID);


    /**
     * Verifies that {@link AbstractKafkaBasedDownstreamSender#start()} creates a producer and
     * {@link AbstractKafkaBasedDownstreamSender#stop()} closes it.
     */
    @Test
    public void testLifecycle() {
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = TestHelper.newProducerFactory(mockProducer);
        final AbstractKafkaBasedDownstreamSender sender = newSender(factory);

        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
        sender.start();
        assertThat(factory.getProducer(PRODUCER_NAME)).isNotEmpty();
        sender.stop();
        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
    }

    /**
     * Verifies that the Kafka record is created as expected.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendCreatesCorrectRecord(final VertxTestContext ctx) {

        // GIVEN a sender
        final String payload = "the-payload";
        final Map<String, Object> properties = Collections.singletonMap("foo", "bar");
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(TestHelper.newProducerFactory(mockProducer));

        // WHEN sending a message
        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, Buffer.buffer(payload), properties, null)
                .onComplete(ctx.succeeding(v -> {

                    final ProducerRecord<String, Buffer> actual = mockProducer.history().get(0);
                    ctx.verify(() -> {
                        // THEN the producer record is created from the given values...
                        assertThat(actual.key()).isEqualTo(DEVICE_ID);
                        assertThat(actual.topic()).isEqualTo(topic.toString());
                        assertThat(actual.value().toString()).isEqualTo(payload);
                        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("foo", "bar".getBytes()));

                        // ...AND contains the standard headers
                        TestHelper.assertStandardHeaders(actual, DEVICE_ID, CONTENT_TYPE, qos);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the send method returns the underlying error wrapped in a {@link ServerErrorException}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendFailsWithTheExpectedError(final VertxTestContext ctx) {

        // GIVEN a sender sending a message
        final RuntimeException expectedError = new RuntimeException("boom");
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(false);
        final AbstractKafkaBasedDownstreamSender sender = newSender(TestHelper.newProducerFactory(mockProducer));

        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, null, null)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN it fails with the expected error
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(503);
                        assertThat(t.getCause()).isEqualTo(expectedError);
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that the producer is closed when sending of a message fails with a fatal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerIsClosedOnFatalError(final VertxTestContext ctx) {

        final AuthorizationException expectedError = new AuthorizationException("go away");

        // GIVEN a sender sending a message
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(false);
        final CachingKafkaProducerFactory<String, Buffer> factory = TestHelper.newProducerFactory(mockProducer);
        newSender(factory).send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, null, null)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the producer is removed and closed
                        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
                        assertThat(mockProducer.closed()).isTrue();
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that the producer is not closed when sending of a message fails with a non-fatal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerIsNotClosedOnNonFatalError(final VertxTestContext ctx) {

        final RuntimeException expectedError = new RuntimeException("foo");

        // GIVEN a sender sending a message
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(false);
        final CachingKafkaProducerFactory<String, Buffer> factory = TestHelper.newProducerFactory(mockProducer);
        newSender(factory).send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, null, null)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the producer is present and still open
                        assertThat(factory.getProducer(PRODUCER_NAME)).isNotEmpty();
                        assertThat(mockProducer.closed()).isFalse();
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);

    }

    /**
     * Verifies that if the properties contain a <em>ttd</em> property but no <em>creation-time</em> then the later is
     * added.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsAddedWhenNotPresentAndTtdIsSet(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(TestHelper.newProducerFactory(mockProducer));

        // GIVEN properties that contain a TTD
        final long ttd = 99L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttd", ttd);

        // WHEN sending the message
        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, properties, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the producer record contains a creation time
                        final ProducerRecord<String, Buffer> record = mockProducer.history().get(0);
                        assertThat(record.headers())
                                .containsOnlyOnce(new RecordHeader("ttd", Json.encode(ttd).getBytes()));
                        assertThat(record.headers().headers("creation-time")).isNotNull();
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies that if the properties contain a <em>creation-time</em> property then it is preserved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsNotChangedWhenPresentAndTtdIsSet(final VertxTestContext ctx) {
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(TestHelper.newProducerFactory(mockProducer));

        // GIVEN properties that contain creation-time
        final long creationTime = 12345L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttd", 99L);
        properties.put("creation-time", creationTime);

        // WHEN sending the message
        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, properties, null)
                .onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the creation time is preserved
                        final ProducerRecord<String, Buffer> record = mockProducer.history().get(0);
                        final RecordHeader expectedHeader = new RecordHeader("creation-time",
                                Json.encode(creationTime).getBytes());
                        assertThat(record.headers()).containsOnlyOnce(expectedHeader);
                    });
                    ctx.completeNow();
                }));

    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final CachingKafkaProducerFactory<String, Buffer> factory = TestHelper.newProducerFactory(mockProducer);

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(null, PRODUCER_NAME, config, tracer) {
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(factory, null, config, tracer) {
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, null, tracer) {
                });

        assertThrows(NullPointerException.class,
                () -> new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, config, null) {
                });
    }

    /**
     * Verifies that
     * {@link AbstractKafkaBasedDownstreamSender#send(HonoTopic, String, String, QoS, String, Buffer, Map, SpanContext)}
     * throws a nullpointer exception if a mandatory parameter is {@code null}.
     */
    @Test
    public void testThatSendThrowsOnMissingMandatoryParameter() {
        final MockProducer<String, Buffer> mockProducer = TestHelper.newMockProducer(true);
        final AbstractKafkaBasedDownstreamSender sender = newSender(TestHelper.newProducerFactory(mockProducer));

        assertThrows(NullPointerException.class,
                () -> sender.send(null, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, null, DEVICE_ID, qos, CONTENT_TYPE, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, TENANT_ID, null, qos, CONTENT_TYPE, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, TENANT_ID, DEVICE_ID, null, CONTENT_TYPE, null, null, null));

        assertThrows(NullPointerException.class,
                () -> sender.send(topic, TENANT_ID, DEVICE_ID, qos, null, null, null, null));

    }

    private AbstractKafkaBasedDownstreamSender newSender(final CachingKafkaProducerFactory<String, Buffer> factory) {
        return new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, config, tracer) {
        };
    }

}
