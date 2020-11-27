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

import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * A factory for creating Kafka consumers.
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public interface KafkaConsumerFactory<K, V> {

    /**
     * Creates a new factory which produces {@link io.vertx.kafka.client.consumer.KafkaConsumer#create(Vertx, Map)}.
     * <p>
     *
     * @param vertx The Vert.x instance to use.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    static <K, V> KafkaConsumerFactory<K, V> consumerFactory(final Vertx vertx) {
        return new CachingKafkaConsumerFactory<>((name, config) -> KafkaConsumer.create(vertx, config));
    }

    /**
     * Gets a consumer for consuming data from a Kafka cluster.
     * <p>
     * The consumer returned may be either newly created or it may be an existing consumer for the given consumer name.
     * The config parameter might be ignored if an existing consumer is returned.
     *
     * @param consumerName The name to identify the consumer.
     * @param config The Kafka configuration with which the consumer is to be created.
     * @return an existing or new consumer.
     */
    KafkaConsumer<K, V> getOrCreateConsumer(String consumerName, Map<String, String> config);

    /**
     * Closes the consumer with the given consumer name if it exists.
     *
     * @param consumerName The name of the consumer to remove.
     * @return A future that is completed when the close operation completed or a succeeded future if no consumer
     *         existed with the given name.
     */
    Future<Void> closeConsumer(String consumerName);

    /**
     * Closes all the active consumers in this factory.
     *
     * @return A future indicating the outcome.
     *         If the future succeeds if all the active consumers have been successfully closed.
     *         Otherwise the future will fail.
     */
    Future<Void> close();
}
