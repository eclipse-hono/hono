/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * Utility methods for working with Kafka Producers.
 */
public final class KafkaProducerHelper {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerHelper.class);

    private KafkaProducerHelper() {
    }

    /**
     * Removes topic-related metrics in the given Kafka producer.
     *
     * @param kafkaProducer The Kafka producer to use.
     * @param topics The topics for which to remove the metrics.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void removeTopicMetrics(final KafkaProducer<?, ?> kafkaProducer, final Stream<String> topics) {
        Objects.requireNonNull(kafkaProducer);
        Objects.requireNonNull(topics);
        final Metrics metrics = getInternalMetricsObject(kafkaProducer.unwrap());
        if (metrics != null) {
            topics.forEach(topic -> {
                metrics.removeSensor("topic." + topic + ".records-per-batch");
                metrics.removeSensor("topic." + topic + ".bytes");
                metrics.removeSensor("topic." + topic + ".compression-rate");
                metrics.removeSensor("topic." + topic + ".record-retries");
                metrics.removeSensor("topic." + topic + ".record-errors");
            });
        }
    }

    private static Metrics getInternalMetricsObject(final Producer<?, ?> producer) {
        if (producer instanceof org.apache.kafka.clients.producer.KafkaProducer) {
            try {
                final Field field = org.apache.kafka.clients.producer.KafkaProducer.class.getDeclaredField("metrics");
                field.setAccessible(true);
                return (Metrics) field.get(producer);
            } catch (final Exception e) {
                LOG.warn("failed to get metrics object", e);
            }
        }
        return null;
    }
}
