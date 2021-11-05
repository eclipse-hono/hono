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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;

/**
 * Configuration properties for Kafka producers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client.
 */
public class KafkaProducerConfigProperties extends AbstractKafkaConfigProperties {

    private final Class<? extends Serializer<?>> keySerializerClass;
    private final Class<? extends Serializer<?>> valueSerializerClass;

    private Map<String, String> producerConfig;

    /**
     * Creates an instance.
     *
     * @param keySerializerClass The class to be used for serializing the record keys, if {@code null} the serializer
     *            needs to be provided in the configuration at runtime.
     * @param valueSerializerClass The class to be used for serializing the record values, if {@code null} the
     */
    protected KafkaProducerConfigProperties(final Class<? extends Serializer<?>> keySerializerClass,
            final Class<? extends Serializer<?>> valueSerializerClass) {

        this.keySerializerClass = keySerializerClass;
        this.valueSerializerClass = valueSerializerClass;
    }

    /**
     * Sets the Kafka producer config properties to be used.
     *
     * @param producerConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setProducerConfig(final Map<String, String> producerConfig) {
        this.producerConfig = Objects.requireNonNull(producerConfig);
    }

    /**
     * Checks if a configuration has been set.
     *
     * @return {@code true} if the {@value #PROPERTY_BOOTSTRAP_SERVERS} property has been configured with a non-null
     *         value.
     */
    public final boolean isConfigured() {
        return containsMinimalConfiguration(commonClientConfig) || containsMinimalConfiguration(producerConfig);
    }

    /**
     * Gets the Kafka producer configuration. This includes changes made in {@link #adaptConfiguration(Map)}. The
     * returned map will contain a property {@code client.id} which will be set to a unique value containing the already
     * set client id or alternatively the value set via {@link #setDefaultClientIdPrefix(String)}, the given
     * producerName and a newly created UUID.
     *
     * Note: This method should be called for each new producer, ensuring that a unique client id is used.
     *
     * @param producerName A name for the producer to include in the added {@code client.id} property.
     * @return a copy of the producer configuration with the applied properties or an empty map if neither a producer
     *         client configuration was set with {@link #setProducerConfig(Map)} nor common configuration properties
     *         were set with {@link #setCommonClientConfig(Map)}.
     * @throws NullPointerException if producerName is {@code null}.
     */
    public final Map<String, String> getProducerConfig(final String producerName) {
        Objects.requireNonNull(producerName);

        final Map<String, String> newConfig = new HashMap<>();
        if (commonClientConfig == null && producerConfig == null) {
            return newConfig;
        }

        if (commonClientConfig != null) {
            newConfig.putAll(commonClientConfig);
        }
        if (producerConfig != null) {
            newConfig.putAll(producerConfig);
        }

        adaptConfiguration(newConfig);

        Optional.ofNullable(keySerializerClass).ifPresent(serializerClass -> overrideConfigProperty(newConfig,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializerClass.getName()));

        Optional.ofNullable(valueSerializerClass).ifPresent(serializerClass -> overrideConfigProperty(newConfig,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializerClass.getName()));

        setUniqueClientId(newConfig, producerName, ProducerConfig.CLIENT_ID_CONFIG);
        return newConfig;
    }

    /**
     * Adapt the properties. It is invoked by {@link #getProducerConfig(String)} on the result of applying the consumer
     * configuration on the common configuration.
     * <p>
     * Subclasses may overwrite this method to set expected configuration values. The default implementation does
     * nothing.
     *
     * @param config The producer configuration to be adapted.
     */
    protected void adaptConfiguration(final Map<String, String> config) {

    }

}
