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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNull;

/**
 * A no-op implementation for the Kafka client metrics support.
 */
public class NoopKafkaClientMetricsSupport implements KafkaClientMetricsSupport {

    /**
     * The shared instance.
     */
    public static final NoopKafkaClientMetricsSupport INSTANCE = new NoopKafkaClientMetricsSupport();

    private NoopKafkaClientMetricsSupport() {
        // prevent instantiation
    }

    @Override
    public void registerKafkaProducer(final Producer<?, ?> producer) {
        // do nothing
    }

    @Override
    public void registerKafkaConsumer(final Consumer<?, ?> consumer) {
        // do nothing
    }

    @Override
    public void unregisterKafkaProducer(final Producer<?, ?> producer) {
        // do nothing
    }

    @Override
    public void unregisterKafkaConsumer(final Consumer<?, ?> consumer) {
        // do nothing
    }

    @Override
    public void bindTo(@NonNull final MeterRegistry registry) {
        // do nothing
    }
}
