/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.consumer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.ConfigOptionsHelper;

/**
 * Configuration properties for Kafka consumers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client.
 */
public class KafkaConsumerConfigProperties extends AbstractKafkaConfigProperties {

    /**
     * The default amount of time (milliseconds) to wait for records when polling records.
     */
    public static final long DEFAULT_POLL_TIMEOUT_MILLIS = 250L; // ms

    private final Class<? extends Deserializer<?>> keyDeserializerClass;
    private final Class<? extends Deserializer<?>> valueDeserializerClass;

    private long pollTimeout = DEFAULT_POLL_TIMEOUT_MILLIS;

    /**
     * Creates an instance.
     *
     * @param keyDeserializerClass The class to be used for deserializing the record keys, if {@code null} the
     *            deserializer needs to be provided in the configuration at runtime.
     * @param valueDeserializerClass The class to be used for deserializing the record values, if {@code null} the
     *            deserializer needs to be provided in the configuration at runtime.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected KafkaConsumerConfigProperties(
            final Class<? extends Deserializer<?>> keyDeserializerClass,
            final Class<? extends Deserializer<?>> valueDeserializerClass) {

        this.keyDeserializerClass = Objects.requireNonNull(keyDeserializerClass);
        this.valueDeserializerClass = Objects.requireNonNull(valueDeserializerClass);
    }

    /**
     * Creates an instance using existing options.
     *
     * @param keyDeserializerClass The class to be used for deserializing the record keys, if {@code null} the
     *            deserializer needs to be provided in the configuration at runtime.
     * @param valueDeserializerClass The class to be used for deserializing the record values, if {@code null} the
     *            deserializer needs to be provided in the configuration at runtime.
     * @param commonOptions The common Kafka client options to use.
     * @param options The consumer options to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected KafkaConsumerConfigProperties(
            final Class<? extends Deserializer<?>> keyDeserializerClass,
            final Class<? extends Deserializer<?>> valueDeserializerClass,
            final CommonKafkaClientOptions commonOptions,
            final KafkaConsumerOptions options) {

        this(keyDeserializerClass, valueDeserializerClass);
        Objects.requireNonNull(commonOptions);
        Objects.requireNonNull(options);

        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties(commonOptions);
        setCommonClientConfig(commonConfig);
        setSpecificClientConfig(ConfigOptionsHelper.toStringValueMap(options.consumerConfig()));
        options.pollTimeout().ifPresent(this::setPollTimeout);
    }

    /**
     * Sets the Kafka consumer config properties to be used.
     *
     * @param consumerConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setConsumerConfig(final Map<String, String> consumerConfig) {
        setSpecificClientConfig(consumerConfig);
    }

    /**
     * Gets the Kafka consumer configuration. This is the result of applying the consumer configuration on the common
     * configuration. It includes changes made in {@link #adaptConfiguration(Map)}.
     * <p>
     * It is ensured that the returned map contains a unique {@code client.id}. The client ID will be created from the
     * given client name, followed by a unique ID (containing component identifiers if running in Kubernetes).
     * An already set {@code client.id} property value will be used as prefix for the client ID.
     *
     * Note: This method should be called for each new consumer, ensuring that a unique client id is used.
     *
     * @param consumerName A name for the consumer to include in the added {@code client.id} property.
     * @return a copy of the consumer configuration with the applied properties.
     * @throws NullPointerException if consumerName is {@code null}.
     */
    public final Map<String, String> getConsumerConfig(final String consumerName) {

        final Map<String, String> config = getConfig(consumerName);

        adaptConfiguration(config);

        Optional.ofNullable(keyDeserializerClass).ifPresent(serializerClass -> overrideConfigProperty(config,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, serializerClass.getName()));

        Optional.ofNullable(valueDeserializerClass).ifPresent(serializerClass -> overrideConfigProperty(config,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, serializerClass.getName()));

        return config;
    }

    /**
     * Adapt the properties. It is invoked by {@link #getConsumerConfig(String)} on the result of applying the consumer
     * configuration on the common configuration.
     * <p>
     * Subclasses may overwrite this method to set expected configuration values.
     * This default implementation does nothing.
     * <p>
     * Subclasses overwriting this method and adapting the {@value CommonClientConfigs#BOOTSTRAP_SERVERS_CONFIG} property
     * in it need to also override {@link #getBootstrapServers()}.
     *
     * @param config The consumer configuration to be adapted.
     */
    protected void adaptConfiguration(final Map<String, String> config) {
        // empty default implementation
    }

    /**
     * Sets the timeout for polling records.
     * <p>
     * The default value of this property is {@value #DEFAULT_POLL_TIMEOUT_MILLIS}.
     *
     * @param pollTimeoutMillis The maximum number of milliseconds to wait.
     * @throws IllegalArgumentException if poll timeout is negative.
     */
    public final void setPollTimeout(final long pollTimeoutMillis) {
        if (pollTimeoutMillis < 0) {
            throw new IllegalArgumentException("poll timeout must not be negative");
        } else {
            this.pollTimeout = pollTimeoutMillis;
        }
    }

    /**
     * Gets the timeout for polling records.
     * <p>
     * The default value of this property is {@value #DEFAULT_POLL_TIMEOUT_MILLIS}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final long getPollTimeout() {
        return pollTimeout;
    }
}
