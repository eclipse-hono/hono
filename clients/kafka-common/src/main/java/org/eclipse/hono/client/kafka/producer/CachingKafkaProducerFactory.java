/*
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.UnsupportedForMessageFormatException;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A factory for creating Kafka producers. Created producers are being cached.
 * <p>
 * This implementation provides no synchronization and should not be used by multiple threads. To create producers that
 * can safely be shared between verticle instances, use {@link #sharedFactory(Vertx)}.
 * <p>
 * Producers are closed and removed from the cache if they throw a {@link #isFatalError(Throwable) fatal exception}.
 * This is triggered by {@link KafkaProducer#exceptionHandler(Handler)} and run asynchronously after the
 * {@link io.vertx.kafka.client.producer.impl.KafkaWriteStreamImpl#send(org.apache.kafka.clients.producer.ProducerRecord, Handler)
 * send operation} has finished. A following invocation of {@link #getOrCreateProducer(String, KafkaProducerConfigProperties)}
 * will then return a new instance.
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public class CachingKafkaProducerFactory<K, V> implements KafkaProducerFactory<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(CachingKafkaProducerFactory.class);

    private final Map<String, KafkaProducer<K, V>> activeProducers = new ConcurrentHashMap<>();
    private final KafkaClientFactory kafkaClientFactory;
    private final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier;

    private KafkaClientMetricsSupport metricsSupport;

    /**
     * Creates a new producer factory.
     * <p>
     * Use {@link #sharedFactory(Vertx)} to create producers that can safely be shared between verticle instances.
     *
     * @param vertx The Vert.x instance to use.
     * @param producerInstanceSupplier The function that provides new producer instances. Parameters are the producer
     *            name and the producer configuration.
     */
    private CachingKafkaProducerFactory(final Vertx vertx,
            final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier) {
        this.producerInstanceSupplier = producerInstanceSupplier;
        this.kafkaClientFactory = new KafkaClientFactory(vertx);
    }

    /**
     * Creates a new factory that produces {@link KafkaProducer#createShared(Vertx, String, Map) shared producers}.
     * Shared producers can safely be shared between verticle instances and improve efficiency by leveraging the
     * batching capabilities of the Kafka client.
     *
     * Producers with the same name will be shared between factory instances created via this method.
     * <p>
     * The resources of a shared producer are released when the last producer with a given name is closed.
     *
     * @param vertx The Vert.x instance to use.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    public static <K, V> CachingKafkaProducerFactory<K, V> sharedFactory(final Vertx vertx) {
        return new CachingKafkaProducerFactory<>(vertx, (name, config) -> KafkaProducer.createShared(vertx, name, config));
    }

    /**
     * Creates a new factory that produces Vert.x Kafka producers.
     *
     * The factory will create one producer (if it not already exists) per name. The producers of each instance of the
     * factory are independent of each other. To share producers between Verticles (improves batching), use
     * {@link #sharedFactory(Vertx)} instead.
     *
     * If using multiple instances of the factory
     * <p>
     * Config must always be the same for the same key in
     * {@link #getOrCreateProducer(String, KafkaProducerConfigProperties)}.
     * <p>
     *
     * @param vertx The Vert.x instance to use.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    public static <K, V> CachingKafkaProducerFactory<K, V> nonSharedFactory(final Vertx vertx) {
        return new CachingKafkaProducerFactory<>(vertx, (name, config) -> KafkaProducer.create(vertx, config));
    }

    /**
     * Creates a new producer factory that creates producers from the given function.
     *
     * This provides the flexibility to control how the producers are created and is intended for unit tests.
     *
     * @param vertx The Vert.x instance to use.
     * @param producerInstanceSupplier The function that provides new producer instances. Parameters are the producer
     *            name and the producer configuration.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    public static <K, V> CachingKafkaProducerFactory<K, V> testFactory(
            final Vertx vertx,
            final BiFunction<String, Map<String, String>, KafkaProducer<K, V>> producerInstanceSupplier) {
        return new CachingKafkaProducerFactory<>(vertx, producerInstanceSupplier);
    }

    /**
     * Sets Kafka metrics support with which producers created by this factory will be registered.
     *
     * @param metricsSupport The metrics support.
     */
    @Override
    public final void setMetricsSupport(final KafkaClientMetricsSupport metricsSupport) {
        this.metricsSupport = metricsSupport;
    }

    /**
     * Gets a producer for sending data to Kafka.
     * <p>
     * This method first tries to look up an already existing producer using the given name. If no producer exists yet,
     * a new instance is created using the given factory and put to the cache.
     * <p>
     * The given config is ignored when an existing producer is returned.
     *
     * @param producerName The name to identify the producer.
     * @param config The Kafka configuration with which the producer is to be created.
     * @return an existing or new producer.
     * @throws org.apache.kafka.common.KafkaException If creating the producer failed because of an invalid config or
     *             because bootstrap servers could not be resolved.
     */
    @Override
    public KafkaProducer<K, V> getOrCreateProducer(final String producerName,
            final KafkaProducerConfigProperties config) {

        final AtomicReference<KafkaProducer<K, V>> createdProducer = new AtomicReference<>();
        final KafkaProducer<K, V> kafkaProducer = activeProducers.computeIfAbsent(producerName, (name) -> {
            final Map<String, String> producerConfig = config.getProducerConfig(producerName);
            final String clientId = producerConfig.get(ProducerConfig.CLIENT_ID_CONFIG);
            final KafkaProducer<K, V> producer = producerInstanceSupplier.apply(producerName, producerConfig);
            createdProducer.set(producer);
            return producer.exceptionHandler(getExceptionHandler(producerName, producer, clientId));
        });
        if (metricsSupport != null && kafkaProducer == createdProducer.get()) {
            // metrics registration is somewhat expensive therefore doing it here to keep computation in computeIfAbsent() short
            metricsSupport.registerKafkaProducer(kafkaProducer.unwrap());
        }

        return kafkaProducer;
    }

    @Override
    public Future<KafkaProducer<K, V>> getOrCreateProducerWithRetries(
            final String producerName,
            final KafkaProducerConfigProperties config,
            final BooleanSupplier keepTrying,
            final Duration retriesTimeout) {

        Objects.requireNonNull(producerName);
        Objects.requireNonNull(config);
        Objects.requireNonNull(keepTrying);

        final String bootstrapServersConfig = config.getBootstrapServers();
        return kafkaClientFactory.createClientWithRetries(
                () -> getOrCreateProducer(producerName, config),
                keepTrying,
                bootstrapServersConfig,
                retriesTimeout);
    }

    private Handler<Throwable> getExceptionHandler(
            final String producerName,
            final KafkaProducer<K, V> producer,
            final String clientId) {

        return t -> {
            // this is executed asynchronously after the send operation has finished
            if (isFatalError(t)) {
                LOG.error("fatal producer error occurred, closing producer [clientId: {}]", clientId, t);
                activeProducers.remove(producerName);
                producer.close()
                    .onComplete(ar -> Optional.ofNullable(metricsSupport)
                            .ifPresent(ms -> ms.unregisterKafkaProducer(producer.unwrap())));
            } else {
                LOG.error("producer error occurred [clientId: {}]", clientId, t);
            }
        };
    }

    @Override
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
            return promise.future()
                    .onComplete(ar -> Optional.ofNullable(metricsSupport)
                            .ifPresent(ms -> ms.unregisterKafkaProducer(producer.unwrap())));
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
