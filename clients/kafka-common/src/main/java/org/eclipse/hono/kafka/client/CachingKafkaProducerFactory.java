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
import java.util.Optional;
import java.util.function.BiFunction;

import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.eclipse.hono.kafka.client.test.FakeProducer;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A factory for creating Kafka producers.
 * <p>
 * This implementation provides no synchronization and should not be used by multiple threads.
 * <p>
 * Created producers are being cached.
 * <p>
 * Producers are closed and removed from the cache if they throw a {@link #isFatalError(Throwable) fatal exception}. A
 * following invocation of {@link #getOrCreateProducer(String, Map)} will then return a new instance.
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public class CachingKafkaProducerFactory<K, V> {

    private final Map<String, KafkaProducer<K, V>> activeProducers = new HashMap<>();
    private final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier;

    /**
     * Creates a new producer factory.
     *
     * @param producerInstanceSupplier The function that provides new producer instances.
     */
    private CachingKafkaProducerFactory(
            final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier) {
        this.producerInstanceSupplier = producerInstanceSupplier;
    }

    /**
     * Creates an instance of the factory which produces {@link KafkaProducer#createShared(Vertx, String, Map) shared
     * producers}.
     * <p>
     * Config must always be the same for the same key in {@link #getOrCreateProducer(String, Map)}.
     *
     * @param vertx The Vert.x instance to use.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    public static <K, V> CachingKafkaProducerFactory<K, V> sharedProducerFactory(final Vertx vertx) {
        return new CachingKafkaProducerFactory<>((name, config) -> KafkaProducer.createShared(vertx, name, config));
    }

    /**
     * Creates an instance of the factory which produces {@link FakeProducer}s.
     * <p>
     * This is intended for tests only.
     *
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    public static <K, V> CachingKafkaProducerFactory<K, V> testProducerFactory() {
        return new CachingKafkaProducerFactory<>((name, config) -> new FakeProducer<>());
    }

    /**
     * Gets a producer for sending data to Kafka.
     * <p>
     * This method first tries to look up an already existing producer using the given name. If no producer exists yet,
     * a new instance is created using the given factory and put to the cache.
     * <p>
     * The given config is ignored when an existing producers is returned.
     *
     * @param producerName The name to identify the producer.
     * @param config The Kafka configuration with which the producer is to be created.
     * @return an existing or new producer.
     */
    public KafkaProducer<K, V> getOrCreateProducer(final String producerName, final Map<String, String> config) {

        activeProducers.computeIfAbsent(producerName, (name) -> {
            final KafkaProducer<K, V> producer = producerInstanceSupplier.apply(producerName, config);
            return producer.exceptionHandler(t -> {
                if (isFatalError(t)) {
                    removeProducer(name);
                }
            });
        });

        return activeProducers.get(producerName);
    }

    /**
     * Gets an existing producer.
     *
     * @param producerName The name to look up the producer.
     * @return The producer or {@code null} if the cache does not contain the name.
     */
    public Optional<KafkaProducer<K, V>> getProducer(final String producerName) {
        return Optional.ofNullable(activeProducers.get(producerName));
    }

    /**
     * Removes a producer from the cache. If a producer exists with the given name, it will be closed.
     *
     * @param producerName The name of the producer to remove.
     * @return A future that is completed when the close operation completed.
     */
    public Future<Void> removeProducer(final String producerName) {
        final KafkaProducer<K, V> producer = activeProducers.remove(producerName);
        if (producer == null) {
            return Future.succeededFuture();
        } else {
            final Promise<Void> promise = Promise.promise();
            producer.close(promise);
            return promise.future();
        }
    }

    /**
     * Removes all producers from the cache. The producers will be closed.
     */
    public void removeAll() {
        activeProducers.forEach((k, v) -> v.close());
        activeProducers.clear();
    }

    /**
     * Checks if the given throwable indicates a fatal producer error.
     *
     * @param error The error to be checked.
     * @return {@code true} if error is an instance of one of the following ({@code false} otherwise):
     *         <ul>
     *         <li>{@link ProducerFencedException}</li>
     *         <li>{@link OutOfOrderSequenceException}</li>
     *         <li>{@link AuthorizationException}</li>
     *         <li>{@link UnsupportedVersionException}</li>
     *         <li>{@link UnsupportedForMessageFormatException}.</li>
     *         </ul>
     */
    public static boolean isFatalError(final Throwable error) {
        return error instanceof ProducerFencedException
                || error instanceof OutOfOrderSequenceException
                || error instanceof AuthorizationException
                || error instanceof UnsupportedVersionException
                || error instanceof UnsupportedForMessageFormatException;
    }

}
