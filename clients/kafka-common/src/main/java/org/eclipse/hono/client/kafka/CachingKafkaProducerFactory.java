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

package org.eclipse.hono.client.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException;
import org.apache.kafka.common.errors.UnsupportedVersionException;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A factory for creating Kafka producers. Created producers are being cached.
 * <p>
 * This implementation provides no synchronization and should not be used by multiple threads. To create producers that
 * can safely be shared between verticle instances, use {@link KafkaProducerFactory#sharedProducerFactory(Vertx)}.
 * <p>
 * Producers are closed and removed from the cache if they throw a {@link #isFatalError(Throwable) fatal exception}.
 * This is triggered by {@link KafkaProducer#exceptionHandler(Handler)} and run asynchronously after the
 * {@link io.vertx.kafka.client.producer.impl.KafkaWriteStreamImpl#send(ProducerRecord, Handler) send operation} has
 * finished. A following invocation of {@link #getOrCreateProducer(String, Map)} will then return a new instance.
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public class CachingKafkaProducerFactory<K, V> implements KafkaProducerFactory<K, V> {

    private final Map<String, KafkaProducer<K, V>> activeProducers = new HashMap<>();
    private final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier;

    /**
     * Creates a new producer factory.
     * <p>
     * Use {@link KafkaProducerFactory#sharedProducerFactory(Vertx)} to create producers that can safely be shared
     * between verticle instances.
     *
     * @param producerInstanceSupplier The function that provides new producer instances.
     */
    public CachingKafkaProducerFactory(
            final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier) {
        this.producerInstanceSupplier = producerInstanceSupplier;
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
    @Override
    public KafkaProducer<K, V> getOrCreateProducer(final String producerName, final Map<String, String> config) {

        activeProducers.computeIfAbsent(producerName, (name) -> {
            final KafkaProducer<K, V> producer = producerInstanceSupplier.apply(producerName, config);
            return producer.exceptionHandler(getExceptionHandler(name, producer));
        });

        return activeProducers.get(producerName);
    }

    private Handler<Throwable> getExceptionHandler(final String producerName, final KafkaProducer<K, V> producer) {
        return t -> {
            // this is executed asynchronously after the send operation has finished
            if (isFatalError(t)) {
                activeProducers.remove(producerName);
                producer.close();
            }
        };
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
     * {@inheritDoc}
     * <p>
     * If a producer with the given name exists, it is removed from the cache.
     */
    @Override
    public Future<Void> closeProducer(final String producerName) {
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
