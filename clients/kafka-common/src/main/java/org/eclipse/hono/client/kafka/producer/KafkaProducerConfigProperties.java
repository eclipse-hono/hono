/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.ConfigOptionsHelper;

/**
 * Configuration properties for Kafka producers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client.
 */
public class KafkaProducerConfigProperties extends AbstractKafkaConfigProperties {

    private final Class<? extends Serializer<?>> keySerializerClass;
    private final Class<? extends Serializer<?>> valueSerializerClass;

    /**
     * Creates an instance.
     *
     * @param keySerializerClass The class to be used for serializing the record keys, if {@code null} the serializer
     *            needs to be provided in the configuration at runtime.
     * @param valueSerializerClass The class to be used for serializing the record values, if {@code null} the
     *            serializer needs to be provided in the configuration at runtime.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected KafkaProducerConfigProperties(
            final Class<? extends Serializer<?>> keySerializerClass,
            final Class<? extends Serializer<?>> valueSerializerClass) {

        this.keySerializerClass = keySerializerClass;
        this.valueSerializerClass = valueSerializerClass;
    }

    /**
     * Creates an instance using existing options.
     *
     * @param keySerializerClass The class to be used for serializing the record keys, if {@code null} the serializer
     *            needs to be provided in the configuration at runtime.
     * @param valueSerializerClass The class to be used for serializing the record values, if {@code null} the
     *            serializer needs to be provided in the configuration at runtime.
     * @param commonOptions The common Kafka client options to use.
     * @param options The producer options to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected KafkaProducerConfigProperties(
            final Class<? extends Serializer<?>> keySerializerClass,
            final Class<? extends Serializer<?>> valueSerializerClass,
            final CommonKafkaClientOptions commonOptions,
            final KafkaProducerOptions options) {

        this(keySerializerClass, valueSerializerClass);
        Objects.requireNonNull(commonOptions);
        Objects.requireNonNull(options);

        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties(commonOptions);
        setCommonClientConfig(commonConfig);
        setSpecificClientConfig(ConfigOptionsHelper.toStringValueMap(options.producerConfig()));
    }

    /**
     * Sets the Kafka producer config properties to be used.
     *
     * @param producerConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setProducerConfig(final Map<String, String> producerConfig) {
        setSpecificClientConfig(producerConfig);
    }

    /**
     * Gets the Kafka producer configuration. This is the result of applying the producer configuration on the common
     * configuration. It includes changes made in {@link #adaptConfiguration(Map)}.
     * <p>
     * It is ensured that the returned map contains a unique {@code client.id}. The client ID will be created from the
     * given client name, followed by a unique ID (containing component identifiers if running in Kubernetes).
     * An already set {@code client.id} property value will be used as prefix for the client ID.
     *
     * Note: This method should be called for each new producer, ensuring that a unique client id is used.
     *
     * @param producerName A name for the producer to include in the added {@code client.id} property.
     * @return a copy of the producer configuration with the applied properties.
     * @throws NullPointerException if producerName is {@code null}.
     */
    public final Map<String, String> getProducerConfig(final String producerName) {

        final Map<String, String> config = getConfig(producerName);

        adaptConfiguration(config);

        Optional.ofNullable(keySerializerClass).ifPresent(serializerClass -> overrideConfigProperty(config,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializerClass.getName()));

        Optional.ofNullable(valueSerializerClass).ifPresent(serializerClass -> overrideConfigProperty(config,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializerClass.getName()));

        return config;
    }

    /**
     * Adapt the properties. It is invoked by {@link #getProducerConfig(String)} on the result of applying the producer
     * configuration on the common configuration.
     * <p>
     * Subclasses may overwrite this method to set expected configuration values.
     * This default implementation does nothing.
     * <p>
     * Subclasses overwriting this method and adapting the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} property
     * in it need to also override {@link #getBootstrapServers()}.
     *
     * @param config The producer configuration to be adapted.
     */
    protected void adaptConfiguration(final Map<String, String> config) {
        // empty default implementation
    }
}
