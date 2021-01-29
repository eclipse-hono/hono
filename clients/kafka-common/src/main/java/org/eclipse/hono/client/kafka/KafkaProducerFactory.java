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

import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
     * Creates a new factory that produces {@link KafkaProducer#createShared(Vertx, String, Map) shared producers}.
     * Shared producers can safely be shared between verticle instances.
     * <p>
     * Config must always be the same for the same key in {@link #getOrCreateProducer(String, KafkaProducerConfigProperties)}.
     * <p>
     * The resources of a shared producer are released when the last producer with a given name is closed.
     *
     * @param vertx The Vert.x instance to use.
     * @param <K> The type for the record key serialization.
     * @param <V> The type for the record value serialization.
     * @return An instance of the factory.
     */
    static <K, V> KafkaProducerFactory<K, V> sharedProducerFactory(final Vertx vertx) {
        return new CachingKafkaProducerFactory<>((name, config) -> KafkaProducer.createShared(vertx, name, config));
    }

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
     */
    KafkaProducer<K, V> getOrCreateProducer(String producerName, KafkaProducerConfigProperties config);

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

}
