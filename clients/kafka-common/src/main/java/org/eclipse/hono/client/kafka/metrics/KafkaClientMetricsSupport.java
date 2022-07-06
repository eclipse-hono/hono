/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.metrics;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Provides support for registering Kafka clients from which metrics are fetched.
 */
public interface KafkaClientMetricsSupport extends MeterBinder {

    /**
     * Registers a Kafka producer to fetch metrics from.
     *
     * @param producer The producer to register.
     * @throws NullPointerException if producer is {@code null}.
     */
    void registerKafkaProducer(Producer<?, ?> producer);

    /**
     * Registers a Kafka consumer to fetch metrics from.
     *
     * @param consumer The consumer to register.
     * @throws NullPointerException if consumer is {@code null}.
     */
    void registerKafkaConsumer(Consumer<?, ?> consumer);

    /**
     * Unregisters a Kafka producer so that no metrics are fetched from it anymore.
     *
     * @param producer The producer to unregister.
     * @throws NullPointerException if producer is {@code null}.
     */
    void unregisterKafkaProducer(Producer<?, ?> producer);

    /**
     * Unregisters a Kafka consumer so that no metrics are fetched from it anymore.
     *
     * @param consumer The consumer to unregister.
     * @throws NullPointerException if consumer is {@code null}.
     */
    void unregisterKafkaConsumer(Consumer<?, ?> consumer);
}
