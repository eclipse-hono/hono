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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.kafka.CachingKafkaProducerFactory;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.QoS;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.serialization.BufferSerializer;

/**
 * A helper class for writing tests with the Kafka client.
 */
public class KafkaClientUnitTestHelper {

    private KafkaClientUnitTestHelper() {
    }

    /**
     * Returns a new {@link org.eclipse.hono.client.impl.CachingClientFactory} for the given native {@link Producer}.
     * <p>
     * All producers returned by this factory will use the given native producer instance wrapped in a
     * {@link KafkaProducer}.
     *
     * @param producer The (mock) producer to be wrapped.
     * @return The producer factory.
     */
    public static CachingKafkaProducerFactory<String, Buffer> newProducerFactory(
            final Producer<String, Buffer> producer) {

        final Vertx vertxMock = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertxMock);
        when(vertxMock.getOrCreateContext()).thenReturn(context);

        doAnswer(invocation -> {
            final Promise<RecordMetadata> result = Promise.promise();
            final Handler<Future<RecordMetadata>> blockingCode = invocation.getArgument(0);
            blockingCode.handle(result.future());
            return null;
        }).when(context).executeBlocking(VertxMockSupport.anyHandler(), any());

        return new CachingKafkaProducerFactory<>((n, c) -> KafkaProducer.create(vertxMock, producer));
    }

    /**
     * Returns a new {@link MockProducer}.
     *
     * @param autoComplete If true, the producer automatically completes all requests successfully and executes the
     *            callback. Otherwise the {@link MockProducer#completeNext()} or
     *            {@link MockProducer#errorNext(RuntimeException)} must be invoked after sending a message.
     * @return the mock producer.
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
            final String contentType, final QoS qos) {

        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("content-type", contentType.getBytes()));

        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("device_id", deviceId.getBytes()));

        assertThat(actual.headers()).containsOnlyOnce(new RecordHeader("qos", Json.encode(qos.ordinal()).getBytes()));
    }

}
