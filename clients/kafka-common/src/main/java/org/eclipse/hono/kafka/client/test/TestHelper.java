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

package org.eclipse.hono.kafka.client.test;

import java.util.NoSuchElementException;

import org.apache.kafka.clients.producer.MockProducer;
import org.eclipse.hono.kafka.client.CachingKafkaProducerFactory;

import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A helper class for writing tests with the Kafka client.
 */
public class TestHelper {

    private TestHelper() {
    }

    /**
     * Gets the {@link MockProducer} for the given producer name from a factory.
     *
     * @param producerFactory The factory containing the {@link FakeProducer}.
     * @param producerName The name under which the fake producer is cached in the factory.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return The mock producer.
     *
     * @throws NoSuchElementException if the given factory does not contain the expected producer (e.g. when the
     *             producer got closed after a fatal error).
     * @throws ClassCastException if the provided producer implementation is not an instance of {@link FakeProducer}.
     */
    public static <K, V> MockProducer<K, V> getUnderlyingMockProducer(
            final CachingKafkaProducerFactory<K, V> producerFactory, final String producerName) {

        final KafkaProducer<K, V> kafkaProducer = producerFactory.getProducer(producerName)
                .orElseThrow(() -> new NoSuchElementException("no producer present in producer factory"));

        return getUnderlyingMockProducer(kafkaProducer);
    }

    /**
     * Gets the {@link MockProducer} from a given {@link FakeProducer}.
     *
     * @param kafkaProducer The fake producer to get the mock producer from.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return The mock producer.
     *
     * @throws ClassCastException if the provided producer implementation is not an instance of {@link FakeProducer}.
     */
    public static <K, V> MockProducer<K, V> getUnderlyingMockProducer(
            final KafkaProducer<K, V> kafkaProducer) {

        return ((FakeProducer<K, V>) kafkaProducer).getMockProducer();
    }

}
