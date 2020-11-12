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

package org.eclipse.hono.kafka.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.eclipse.hono.kafka.client.test.FakeProducer;
import org.eclipse.hono.kafka.client.test.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.impl.KafkaProducerImpl;

/**
 * Verifies behavior of {@link CachingKafkaProducerFactory}.
 */
public class CachingKafkaProducerFactoryTest {

    private static final String PRODUCER_NAME = "test-producer";

    private final Map<String, String> config = new HashMap<>();

    private CachingKafkaProducerFactory<String, Buffer> factory;

    @BeforeEach
    void setUp() {
        factory = CachingKafkaProducerFactory.testProducerFactory();

        config.put("bootstrap.servers", "localhost:9092");
        config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    }

    /**
     * Verifies that getOrCreateProducer() creates a producers and adds it to the cache.
     */
    @Test
    public void testThatProducerIsAddedToCache() {

        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();

        final KafkaProducer<String, Buffer> createProducer = factory.getOrCreateProducer(PRODUCER_NAME,
                config);
        assertThat(createProducer).isNotNull();

        final Optional<KafkaProducer<String, Buffer>> actual = factory.getProducer(PRODUCER_NAME);
        assertThat(actual).isNotEmpty();
        assertThat(actual.get()).isEqualTo(createProducer);

    }

    /**
     * Verifies that {@link CachingKafkaProducerFactory#sharedProducerFactory(Vertx)} creates producers that are
     * instances of {@link KafkaProducerImpl}.
     */
    @Test
    public void testThatSharedProducerFactoryCreatesVertxKafkaProducers() {

        final CachingKafkaProducerFactory<String, Buffer> productiveFactory = CachingKafkaProducerFactory
                .sharedProducerFactory(mock(VertxInternal.class));
        assertThat(productiveFactory.getOrCreateProducer(PRODUCER_NAME, config)).isInstanceOf(KafkaProducerImpl.class);

    }

    /**
     * Verifies that {@link CachingKafkaProducerFactory#testProducerFactory()} (Vertx)} creates producers that are
     * instances of {@link FakeProducer}.
     */
    @Test
    public void testThatSharedProducerFactoryCreatesFakeProducers() {

        final CachingKafkaProducerFactory<String, Buffer> fakeProducerFactory = CachingKafkaProducerFactory
                .testProducerFactory();
        assertThat(fakeProducerFactory.getOrCreateProducer(PRODUCER_NAME, config)).isInstanceOf(FakeProducer.class);

    }

    /**
     * Verifies that {@link CachingKafkaProducerFactory#removeProducer(String)} closes the producer and removes it from
     * the cache.
     */
    @Test
    public void testRemoveProducerClosesAndRemovesFromCache() {

        // GIVEN a factory that contains two producers
        final KafkaProducer<String, Buffer> producer1 = factory.getOrCreateProducer(PRODUCER_NAME, config);
        final MockProducer<String, Buffer> mockProducer = TestHelper.getUnderlyingMockProducer(producer1);

        final String producerName2 = "second-producer";
        final KafkaProducer<String, Buffer> producer2 = factory.getOrCreateProducer(producerName2, config);
        assertThat(producer2).isNotNull();

        // WHEN removing one producer
        factory.removeProducer(PRODUCER_NAME);

        // THEN the producer is closed...
        assertThat(mockProducer.closed()).isTrue();
        // ...AND removed from the cache
        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
        // ...AND the second producers is still present and open
        assertThat(factory.getProducer(producerName2)).isNotEmpty();
        assertThat(TestHelper.getUnderlyingMockProducer(producer2).closed()).isFalse();

    }

    /**
     * Verifies that {@link CachingKafkaProducerFactory#removeAll()} closes and removes all producers.
     */
    @Test
    public void testRemoveAll() {
        final String producerName2 = "second-producer";

        // GIVEN a factory that contains two producers
        final KafkaProducer<String, Buffer> producer1 = factory.getOrCreateProducer(PRODUCER_NAME, config);
        final MockProducer<String, Buffer> mockProducer1 = TestHelper.getUnderlyingMockProducer(producer1);

        final KafkaProducer<String, Buffer> producer2 = factory.getOrCreateProducer(producerName2, config);
        final MockProducer<String, Buffer> mockProducer2 = TestHelper.getUnderlyingMockProducer(producer2);

        // WHEN invoking remove all
        factory.removeAll();

        // THEN both producers are closed...
        assertThat(mockProducer1.closed()).isTrue();
        assertThat(mockProducer2.closed()).isTrue();
        // ...AND removed from the cache
        assertThat(factory.getProducer(PRODUCER_NAME)).isEmpty();
        assertThat(factory.getProducer(producerName2)).isEmpty();
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
