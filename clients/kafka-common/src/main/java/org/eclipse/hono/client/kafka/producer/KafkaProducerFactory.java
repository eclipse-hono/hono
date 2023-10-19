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

package org.eclipse.hono.client.kafka.producer;

import java.time.Duration;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.hono.client.kafka.metrics.KafkaClientMetricsSupport;

import io.vertx.core.Future;
import io.vertx.kafka.client.producer.KafkaProducer;

/**
 * A factory for creating Kafka producers.
 * <p>
 * Vert.x expects producers to be closed when they are no longer needed to close all open connections and release its
 * resources. Producers are expected to be closed before the application shuts down (even if this is done automatically
 * for producers that are created from inside of verticles).
 *
 * @param <K> The type for the record key serialization.
 * @param <V> The type for the record value serialization.
 */
public interface KafkaProducerFactory<K, V> {

    /**
     * Gets an existing producer for sending data to Kafka, if one was already created with the given producer name.
     *
     * @param producerName The name to identify the producer.
     * @return An existing producer or an empty Optional if no such producer exists.
     */
    Optional<KafkaProducer<K, V>> getProducer(String producerName);

    /**
     * Gets a producer for sending data to Kafka.
     * <p>
     * The producer returned may be either newly created or it may be an existing producer for the given producer name.
     * The config parameter might be ignored if an existing producer is returned.
     * <p>
     * Do not hold references to the returned producer between send operations, because the producer might be closed by
     * the factory. Instead, always get an instance by invoking this method.
     * <p>
     * When the producer is no longer needed, it should be closed to release the resources (like connections). NB: Do
     * not close the returned producer directly. Instead, call {@link #closeProducer(String)} to let the factory close
     * the producer and update its internal state.
     *
     * @param producerName The name to identify the producer.
     * @param config The Kafka configuration with which the producer is to be created.
     * @return an existing or new producer.
     * @throws org.apache.kafka.common.KafkaException If creating the producer failed because of an invalid config or
     *             because bootstrap servers could not be resolved.
     */
    KafkaProducer<K, V> getOrCreateProducer(String producerName, KafkaProducerConfigProperties config);

    /**
     * Gets a producer for sending data to Kafka.
     * <p>
     * If producer creation fails because the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} config property
     * contains a (non-empty) list of URLs that are not (yet) resolvable, further creation attempts are done with some
     * delay in between. These retries are done until the given <em>retriesTimeout</em> has elapsed.
     * <p>
     * The producer returned may be either newly created or it may be an existing producer for the given producer name.
     * The config parameter might be ignored if an existing producer is returned.
     * <p>
     * Do not hold references to the returned producer between send operations, because the producer might be closed by
     * the factory. Instead, always get an instance by invoking this method.
     * <p>
     * When the producer is no longer needed, it should be closed to release the resources (like connections). NB: Do
     * not close the returned producer directly. Instead, call {@link #closeProducer(String)} to let the factory close
     * the producer and update its internal state.
     *
     * @param producerName The name to identify the producer.
     * @param config The Kafka configuration with which the producer is to be created.
     * @param keepTrying A guard condition that controls whether another attempt to create the producer should be
     *                     started. Client code can set this to {@code false} in order to prevent any further attempts.
     * @param retriesTimeout The maximum time for which retries are done. Using a negative duration or {@code null}
     *                       here is interpreted as an unlimited timeout value.
     * @return A future with an existing or new producer.
     * @throws NullPointerException if any of the parameters except retries timeout are {@code null}.
     */
    Future<KafkaProducer<K, V>> getOrCreateProducerWithRetries(
            String producerName,
            KafkaProducerConfigProperties config,
            BooleanSupplier keepTrying,
            Duration retriesTimeout);

    /**
     * Closes the producer with the given producer name if it exists.
     * <p>
     * This method is expected to be invoked as soon as the producer is no longer needed, especially before the
     * application shuts down.
     *
     * @param producerName The name of the producer to remove.
     * @return A future that is completed when the close operation completed or a succeeded future if no producer
     *         existed with the given name.
     */
    Future<Void> closeProducer(String producerName);

    /**
     * Sets Kafka metrics support with which producers created by this factory will be registered.
     *
     * @param metricsSupport The metrics support.
     */
    void setMetricsSupport(KafkaClientMetricsSupport metricsSupport);

}
