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

package org.eclipse.hono.client.kafka.producer;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.serialization.BufferSerializer;

/**
 * Verifies behavior of {@link CachingKafkaProducerFactory}.
 */
public class CachingKafkaProducerFactoryTest {

    private static final String PRODUCER_NAME = "test-producer";

    private final MessagingKafkaProducerConfigProperties configProperties = new MessagingKafkaProducerConfigProperties();

    private CachingKafkaProducerFactory<String, Buffer> factory;

    @BeforeEach
    void setUp() {

        final VertxInternal vertxMock = mock(VertxInternal.class);
        final ContextInternal context = VertxMockSupport.mockContextInternal(vertxMock);
        final PromiseInternal<Void> promiseInternal = VertxMockSupport.promiseInternal();
        when(promiseInternal.future()).thenReturn(Future.succeededFuture());
        doAnswer(invocation -> {
            return promiseInternal;
        }).when(context).promise();
        when(vertxMock.getOrCreateContext()).thenReturn(context);

        doAnswer(invocation -> {
            final Promise<Object> result = Promise.promise();
            final Handler<Future<Object>> blockingCode = invocation.getArgument(0);
            final Handler<AsyncResult<Object>> resultHandler = invocation.getArgument(1);
            result.future().onComplete(resultHandler);
            blockingCode.handle(result.future());
            return null;
        }).when(context).executeBlocking(VertxMockSupport.anyHandler(), VertxMockSupport.anyHandler());

        final BiFunction<String, Map<String, String>, KafkaProducer<String, Buffer>> instanceSupplier = (n, c) -> {
            final MockProducer<String, Buffer> mockProducer = new MockProducer<>(true, new StringSerializer(),
                    new BufferSerializer());
            return KafkaProducer.create(vertxMock, mockProducer);
        };

        factory = CachingKafkaProducerFactory.testFactory(vertxMock, instanceSupplier);

        configProperties.setProducerConfig(Map.of("bootstrap.servers", "localhost:9092"));
    }

    /**
     * Verifies that getOrCreateProducer() creates a producers and adds it to the cache.
     */
    @Test
    public void testThatProducerIsAddedToCache() {

        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();

        final KafkaProducer<String, Buffer> createProducer = factory.getOrCreateProducer(PRODUCER_NAME,
                configProperties);

        final Optional<KafkaProducer<String, Buffer>> actual = factory.getProducer(PRODUCER_NAME);
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(createProducer);

    }

    /**
     * Verifies that {@link CachingKafkaProducerFactory#closeProducer(String)} closes the producer and removes it from
     * the cache.
     */
    @Test
    public void testRemoveProducerClosesAndRemovesFromCache() {
        final String producerName1 = "first-producer";
        final String producerName2 = "second-producer";

        // GIVEN a factory that contains two producers
        final KafkaProducer<String, Buffer> producer1 = factory.getOrCreateProducer(producerName1, configProperties);
        final KafkaProducer<String, Buffer> producer2 = factory.getOrCreateProducer(producerName2, configProperties);

        // WHEN removing one producer
        factory.closeProducer(producerName1);

        // THEN the producer is closed...
        assertThat(((MockProducer<String, Buffer>) producer1.unwrap()).closed()).isTrue();
        // ...AND removed from the cache
        assertThat(factory.getProducer(producerName1).isEmpty()).isTrue();
        // ...AND the second producers is still present and open
        assertThat(factory.getProducer(producerName2).isPresent()).isTrue();
        assertThat(((MockProducer<String, Buffer>) producer2.unwrap()).closed()).isFalse();

    }

    /**
     * Verifies that {@link CachingKafkaProducerFactory#isFatalError(Throwable)} returns true for the expected exception
     * types.
     */
    @Test
    public void testIsFatalError() {

        assertThat(CachingKafkaProducerFactory.isFatalError(new ProducerFencedException("test"))).isTrue();
        assertThat(CachingKafkaProducerFactory.isFatalError(new OutOfOrderSequenceException("test"))).isTrue();
        assertThat(CachingKafkaProducerFactory.isFatalError(new AuthorizationException("test"))).isTrue();
        assertThat(CachingKafkaProducerFactory.isFatalError(new UnsupportedVersionException("test"))).isTrue();
        assertThat(CachingKafkaProducerFactory.isFatalError(new UnsupportedForMessageFormatException("test"))).isTrue();

        assertThat(CachingKafkaProducerFactory.isFatalError(new KafkaException("test"))).isFalse();
    }
}
