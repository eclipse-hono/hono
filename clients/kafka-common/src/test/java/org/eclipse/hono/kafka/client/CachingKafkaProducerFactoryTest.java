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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.eclipse.hono.kafka.test.FakeProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * Verifies behavior of {@link CachingKafkaProducerFactory}.
 */
public class CachingKafkaProducerFactoryTest {

    private static final String PRODUCER_NAME = "test-producer";

    private final Map<String, String> config = new HashMap<>();

    private CachingKafkaProducerFactory<String, Buffer> factory;

    @BeforeEach
    void setUp() {
        factory = new CachingKafkaProducerFactory<>((name, config1) -> new FakeProducer<>());

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

        final KafkaProducer<String, Buffer> createProducer = factory.getOrCreateProducer(PRODUCER_NAME, config);
        assertThat(createProducer).isNotNull();

        final Optional<KafkaProducer<String, Buffer>> actual = factory.getProducer(PRODUCER_NAME);
        assertThat(actual).isNotEmpty();
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
        final KafkaProducer<String, Buffer> producer1 = factory.getOrCreateProducer(producerName1, config);
        final KafkaProducer<String, Buffer> producer2 = factory.getOrCreateProducer(producerName2, config);
        assertThat(producer2).isNotNull();

        // WHEN removing one producer
        factory.closeProducer(producerName1);

        // THEN the producer is closed...
        assertThat(((FakeProducer<String, Buffer>) producer1).getMockProducer().closed()).isTrue();
        // ...AND removed from the cache
        assertThat(factory.getProducer(producerName1)).isEmpty();
        // ...AND the second producers is still present and open
        assertThat(factory.getProducer(producerName2)).isNotEmpty();
        assertThat(((FakeProducer<String, Buffer>) producer2).getMockProducer().closed()).isFalse();

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
