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

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.kafka.client.CachingKafkaProducerFactory;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.test.FakeProducer;
import org.eclipse.hono.util.QoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxExtension;

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

    private final Tracer tracer = NoopTracerFactory.create();
    private final HashMap<String, String> config = new HashMap<>();
    private final HonoTopic topic = new HonoTopic(HonoTopic.Type.EVENT, "foo");

    private CachingKafkaProducerFactory<String, Buffer> factory;
    private AbstractKafkaBasedDownstreamSender sender;

    /**
     * Sets up the downstream sender.
     */
    @BeforeEach
    public void setUp() {
        config.put("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092");

        factory = new CachingKafkaProducerFactory<>((n, c) -> new FakeProducer<>());

        sender = new AbstractKafkaBasedDownstreamSender(factory, PRODUCER_NAME, config, tracer) {
        };
    }

    /**
     * Verifies that {@link AbstractKafkaBasedDownstreamSender#start()} creates a producer and
     * {@link AbstractKafkaBasedDownstreamSender#stop()} closes it.
     */
    @Test
    public void testLifecycle() {
        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
        sender.start();
        assertThat(factory.getProducer(PRODUCER_NAME)).isNotEmpty();
        sender.stop();
        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
    }

    /**
     * Verifies that the Kafka record is created as expected.
     */
    @Test
    public void testSendCreatesCorrectRecord() {

        // GIVEN a sender
        final String payload = "the-payload";
        final Map<String, Object> properties = Collections.singletonMap("foo", "bar");

        // WHEN sending a message
        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, Buffer.buffer(payload), properties, null);

        // THEN the producer record is created from the given values...
        final ProducerRecord<String, Buffer> actual = TestHelper.getUnderlyingMockProducer(factory, PRODUCER_NAME)
                .history().get(0);

        assertThat(actual.key()).isEqualTo(DEVICE_ID);
        assertThat(actual.topic()).isEqualTo(topic.toString());
        assertThat(actual.value().toString()).isEqualTo(payload);
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("foo", "bar".getBytes()));

        // ...AND contains the standard headers
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("content-type", CONTENT_TYPE.getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("device_id", DEVICE_ID.getBytes()));
        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("qos", Json.encode(qos.ordinal()).getBytes()));

    }

    /**
     * Verifies that the future returned by
     * {@link AbstractKafkaBasedDownstreamSender#send(HonoTopic, String, String, QoS, String, Buffer, Map, SpanContext)}
     * completes successfully when the send operation of the Kafka client succeeds.
     */
    @Test
    public void testThatSendCompletes() {

        // GIVEN a sender sending a message
        final Future<Void> sendFuture = sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, null, null);

        // WHEN the send operation completes successfully
        TestHelper.getUnderlyingMockProducer(factory, PRODUCER_NAME).completeNext();

        // THEN the returned future also succeeds
        assertThat(sendFuture.isComplete()).isTrue();
        assertThat(sendFuture.succeeded()).isTrue();
    }

    /**
     * Verifies that the producer is closed when sending of a message fails with a fatal error.
     */
    @Test
    public void testSenderClosesWhenSendFails() {

        // GIVEN a sender sending a message
        final Future<Void> future = sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, null, null);

        // WHEN the send operation fails
        final AuthorizationException expectedError = new AuthorizationException("go away");
        TestHelper.getUnderlyingMockProducer(factory, PRODUCER_NAME).errorNext(expectedError);

        // THEN the returned future is failed...
        assertThat(future.isComplete()).isTrue();
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(ServerErrorException.class);
        assertThat(future.cause().getCause()).isEqualTo(expectedError);

        // ... AND the producer is closed
        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();

    }

    /**
     * Verifies that if the properties contain a <em>ttd</em> property but no <em>creation-time</em> then the later is
     * added.
     */
    @Test
    public void testThatCreationTimeIsAddedWhenNotPresentAndTtdIsSet() {

        // GIVEN properties that contain a TTD
        final long ttd = 99L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttd", ttd);

        // WHEN sending the message
        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, properties, null);

        // THEN the producer record contains a creation time
        final ProducerRecord<String, Buffer> record = TestHelper.getUnderlyingMockProducer(factory, PRODUCER_NAME)
                .history().get(0);

        assertThat(record.headers()).containsOnlyOnce(new RecordHeader("ttd", Json.encode(ttd).getBytes()));
        assertThat(record.headers().headers("creation-time")).isNotNull();

    }

    /**
     * Verifies that if the properties contain a <em>creation-time</em> property then it is preserved.
     */
    @Test
    public void testThatCreationTimeIsNotChangedWhenPresentAndTtdIsSet() {

        // GIVEN properties that contain creation-time
        final long creationTime = 12345L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("ttd", 99L);
        properties.put("creation-time", creationTime);

        // WHEN sending the message
        sender.send(topic, TENANT_ID, DEVICE_ID, qos, CONTENT_TYPE, null, properties, null);

        // THEN the creation time is preserved
        final ProducerRecord<String, Buffer> record = TestHelper.getUnderlyingMockProducer(factory, PRODUCER_NAME)
                .history().get(0);

        final RecordHeader expectedHeader = new RecordHeader("creation-time", Json.encode(creationTime).getBytes());

        assertThat(record.headers()).containsOnlyOnce(expectedHeader);

    }

    /**
     * Verifies that the constructor throws a nullpointer exception if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {

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
}
