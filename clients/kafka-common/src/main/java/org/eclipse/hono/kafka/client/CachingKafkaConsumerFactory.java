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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * TODO.
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public class CachingKafkaConsumerFactory<K, V> implements KafkaConsumerFactory<K, V> {
    private final Map<String, KafkaConsumer<K, V>> activeConsumers = new HashMap<>();
    private final BiFunction<String, Map<String, String>, KafkaConsumer<K, V>> consumerInstanceSupplier;

    /**
     * Creates a new consumer factory.
     * <p>
     * Use {@link KafkaConsumerFactory#consumerFactory(Vertx)} to create consumers.
     *
     * @param consumerInstanceSupplier The function that provides new consumer instances.
     */
    public CachingKafkaConsumerFactory(
            final BiFunction<String, Map<String, String>, KafkaConsumer<K, V>> consumerInstanceSupplier) {
        this.consumerInstanceSupplier = consumerInstanceSupplier;
    }

    @Override
    public KafkaConsumer<K, V> getOrCreateConsumer(final String consumerName,
            final Map<String, String> config) {
        activeConsumers.computeIfAbsent(consumerName, (name) -> {
            final KafkaConsumer<K, V> consumer = consumerInstanceSupplier.apply(consumerName, config);
            return consumer.exceptionHandler(t -> {
                //TODO: check for errors and close
                    closeConsumer(name);
            });
        });

        return activeConsumers.get(consumerName);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If a consumer with the given name exists, it is removed from the cache.
     */
    @Override
    public Future<Void> closeConsumer(final String consumerName) {
        final KafkaConsumer<K, V> consumer = activeConsumers.remove(consumerName);
        if (consumer == null) {
            return Future.succeededFuture();
        } else {
            final Promise<Void> promise = Promise.promise();
            consumer.close(promise);
            return promise.future();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> close() {
        // TODO.
        return CompositeFuture.all(activeConsumers
                .keySet()
                .stream()
                .map(this::closeConsumer)
                .collect(Collectors.toList())).mapEmpty();
    }

}
