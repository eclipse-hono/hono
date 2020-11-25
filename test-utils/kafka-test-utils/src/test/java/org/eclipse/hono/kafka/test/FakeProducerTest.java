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

package org.eclipse.hono.kafka.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Handler;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.impl.KafkaProducerRecordImpl;

/**
 * Verifies the behavior of {@link FakeProducer}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 1, timeUnit = TimeUnit.SECONDS)
public class FakeProducerTest {

    private static final String TOPIC = "the-topic";
    private static final String KEY = "the-key";
    private static final String VALUE = "the-value";
    private static final Long TIMESTAMP = 1234L;
    private static final Integer PARTITION = 11;

    private FakeProducer<String, String> fakeProducer;
    private KafkaProducerRecord<String, String> producerRecord;

    @BeforeEach
    void setUp() {
        fakeProducer = new FakeProducer<>();

        producerRecord = new KafkaProducerRecordImpl<>(TOPIC, KEY, VALUE, TIMESTAMP, PARTITION);
    }

    /**
     * Verifies that the default constructor actually creates a mock producer.
     */
    @Test
    public void testThatDefaultConstructorCreatesAMockProducer() {
        assertThat(new FakeProducer<>().getMockProducer()).isInstanceOf(MockProducer.class);
    }

    /**
     * Verifies that the mock producer provided to {@link FakeProducer#FakeProducer(MockProducer)} is used.
     */
    @Test
    public void testThatMockProducerProvidedToConstructorIsUsed() {

        final MockProducer<String, String> mockProducer = new MockProducer<>(true, new StringSerializer(),
                new StringSerializer());
        assertThat(new FakeProducer<>(mockProducer).getMockProducer()).isSameAs(mockProducer);

    }

    /**
     * Verifies that {@link FakeProducer#send(KafkaProducerRecord, Handler)} sends the producer record with the mock
     * producer.
     */
    @Test
    public void testThatProducerRecordIsSend() {

        // GIVEN a fake producer

        // WHEN sending a record
        fakeProducer.send(producerRecord, ar -> {
        });

        // THEN the record is send with the mock producer
        final ProducerRecord<String, String> sent = fakeProducer.getMockProducer().history().get(0);
        assertThat(sent.topic()).isEqualTo(TOPIC);
        assertThat(sent.key()).isEqualTo(KEY);
        assertThat(sent.value()).isEqualTo(VALUE);
        assertThat(sent.timestamp()).isEqualTo(TIMESTAMP);
        assertThat(sent.partition()).isEqualTo(PARTITION);
    }

    /**
     * Verifies that {@link FakeProducer#send(KafkaProducerRecord, Handler)} completes the handler.
     */
    @Test
    public void testThatSendCompletesSuccessfully() {

        // GIVEN a fake producer that sends a record
        fakeProducer.send(producerRecord, ar -> {
            // THEN the handler completes successfully
            assertThat(ar.succeeded()).isTrue();
        });

        // WHEN completing the send operation
        fakeProducer.getMockProducer().completeNext();
    }

    /**
     * Verifies that an error in {@link FakeProducer#send(KafkaProducerRecord, Handler)} fails the handler.
     */
    @Test
    public void testThatSendWithErrorFailsTheHandler() {

        final RuntimeException expected = new RuntimeException("foo");

        // GIVEN a fake producer that sends a record
        fakeProducer.send(producerRecord, ar -> {
            // THEN the handler completes with the expected exception
            assertThat(ar.failed()).isTrue();
            assertThat(ar.cause()).isSameAs(expected);
        });

        // WHEN failing the send operation with an exception
        fakeProducer.getMockProducer().errorNext(expected);
    }

    /**
     * Verifies that if an exception handler is set, it gets called when
     * {@link FakeProducer#send(KafkaProducerRecord, Handler)} fails.
     */
    @Test
    public void testThatExceptionHandlerIsCalledOnError() {

        final RuntimeException expected = new RuntimeException("foo");

        // GIVEN a fake producer with an exception handler set that sends a record
        fakeProducer
                .exceptionHandler(error -> {
                    // THEN the handler completes with the expected exception
                    assertThat(error).isSameAs(expected);
                })
                .send(producerRecord, null);

        // WHEN failing the send operation with an exception
        fakeProducer.getMockProducer().errorNext(expected);
    }

    /**
     * Verifies that {@link FakeProducer#close(long, Handler)} closes the mock producer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatFakeProducerIsClosed(final VertxTestContext ctx) {

        final FakeProducer<String, String> fake = new FakeProducer<>();
        fake.close(0L, ctx.succeeding(response -> ctx.verify(() -> {
            assertThat(fake.getMockProducer().closed()).isTrue();
            ctx.completeNow();
        })));

    }

}
