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
import static com.google.common.truth.Truth.assertThat;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.test.VertxMockSupport;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.Json;
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
     * Asserts that a given Kafka producer record contains the standard headers only once and with the expected values.
     * The following headers are expected:
     * <ul>
     * <li><em>content-type</em></li>
     * <li><em>device_id</em></li>
     * <li><em>qos</em></li>
     * </ul>
     *
     * @param actual The record to be checked.
     * @param deviceId The expected device ID.
     * @param contentType The expected content type.
     * @param qos The expected QoS level.
     */
    public static void assertStandardHeaders(final ProducerRecord<String, Buffer> actual, final String deviceId,
            final String contentType, final int qos) {

        assertThat(actual.headers().headers("content-type")).hasSize(1);
        assertThat(actual.headers()).contains(new RecordHeader("content-type", contentType.getBytes()));

        assertThat(actual.headers().headers("device_id")).hasSize(1);
        assertThat(actual.headers()).contains(new RecordHeader("device_id", deviceId.getBytes()));

        assertThat(actual.headers().headers("qos")).hasSize(1);
        assertThat(actual.headers()).contains(new RecordHeader("qos", Json.encode(qos).getBytes()));
    }

}
