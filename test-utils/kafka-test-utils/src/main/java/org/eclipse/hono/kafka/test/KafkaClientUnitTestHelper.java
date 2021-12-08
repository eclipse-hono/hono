/*
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
 */

package org.eclipse.hono.kafka.test;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.test.VertxMockSupport;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.RecordMetadata;
import io.vertx.kafka.client.serialization.BufferSerializer;

/**
 * A helper class for writing tests with the Kafka client.
 */
public class KafkaClientUnitTestHelper {

    private KafkaClientUnitTestHelper() {
    }

    /**
     * Creates a consumer record for a topic, key and headers.
     *
     * @param <K> The type of the record's key.
     * @param <P> The type of the record's payload.
     * @param topic The record's topic.
     * @param key The record's key.
     * @param headers The record's headers.
     * @return The record.
     */
    public static <K, P> KafkaConsumerRecord<K, P> newMockConsumerRecord(
            final String topic,
            final K key,
            final List<KafkaHeader> headers) {

        @SuppressWarnings("unchecked")
        final KafkaConsumerRecord<K, P> consumerRecord = mock(KafkaConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.topic()).thenReturn(topic);
        when(consumerRecord.key()).thenReturn(key);
        return consumerRecord;
    }

    /**
     * Returns a new {@link KafkaProducer}.
     *
     * @param producer The mock producer to wrap.
     * @param <K> The type of the key.
     * @param <V> The type of the value.
     * @return The new Kafka producer.
     */
    public static <K, V> KafkaProducer<K, V> newKafkaProducer(final MockProducer<K, V> producer) {

        final VertxInternal vertxMock = mock(VertxInternal.class);
        final ContextInternal context = VertxMockSupport.mockContextInternal(vertxMock);
        doAnswer(invocation -> Promise.promise())
                .when(context).promise();

        doAnswer(invocation -> {
            final Promise<RecordMetadata> result = Promise.promise();
            final Handler<Promise<RecordMetadata>> handler = invocation.getArgument(0);
            handler.handle(result);
            return result.future();
        }).when(context).executeBlocking(VertxMockSupport.anyHandler());
        VertxMockSupport.executeBlockingCodeImmediately(vertxMock, context);

        return KafkaProducer.create(vertxMock, producer);
    }

    /**
     * Returns a new {@link MockProducer}.
     *
     * @param autoComplete If true, the producer automatically completes all requests successfully and executes the
     *            callback. Otherwise, the {@link MockProducer#completeNext()} or
     *            {@link MockProducer#errorNext(RuntimeException)} must be invoked after sending a message.
     * @return The new mock producer.
     */
    public static MockProducer<String, Buffer> newMockProducer(final boolean autoComplete) {
        return new MockProducer<>(autoComplete, new StringSerializer(), new BufferSerializer());
    }

    /**
     * Asserts existence of a unique header value.
     *
     * @param headers The headers to check.
     * @param key The name of the header.
     * @param expectedValue The expected value.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws AssertionError if the headers do not contain a single occurrence of the given key with
     *                        the given value.
     */
    public static void assertUniqueHeaderWithExpectedValue(
            final Headers headers,
            final String key,
            final Object expectedValue) {

        Objects.requireNonNull(headers);
        Objects.requireNonNull(key);
        Objects.requireNonNull(expectedValue);

        final String encodedValue;
        if (expectedValue instanceof String) {
            encodedValue = (String) expectedValue;
        } else {
            encodedValue = Json.encode(expectedValue);
        }

        assertThat(headers.headers(key)).hasSize(1);
        assertThat(headers).contains(new RecordHeader(key, encodedValue.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Asserts that a given Kafka producer record contains the standard headers only once and with the expected values.
     * The following headers are expected:
     * <ul>
     * <li><em>content-type</em></li>
     * <li><em>creation-time</em></li>
     * <li><em>device_id</em></li>
     * <li><em>qos</em></li>
     * </ul>
     *
     * @param actual The record to be checked.
     * @param deviceId The expected device ID.
     * @param contentType The expected content type.
     * @param qos The expected QoS level.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertStandardHeaders(
            final ProducerRecord<String, Buffer> actual,
            final String deviceId,
            final String contentType,
            final int qos) {

        final Headers headers = actual.headers();
        assertUniqueHeaderWithExpectedValue(headers, "content-type", contentType);

        final var creationTimeHeader = headers.headers("creation-time");
        assertThat(creationTimeHeader).hasSize(1);
        final Long creationTimeMillis = Json.decodeValue(
                Buffer.buffer(creationTimeHeader.iterator().next().value()),
                Long.class);
        assertThat(creationTimeMillis).isGreaterThan(0L);

        assertUniqueHeaderWithExpectedValue(headers, "device_id", deviceId);
        assertUniqueHeaderWithExpectedValue(headers, "qos", qos);
    }

}
