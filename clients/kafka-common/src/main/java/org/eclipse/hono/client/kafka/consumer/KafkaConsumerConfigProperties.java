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

package org.eclipse.hono.client.kafka.consumer;

import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
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
    public static final long DEFAULT_POLL_TIMEOUT = 100L; // ms

    private final Class<? extends Deserializer<?>> keyDeserializerClass;
    private final Class<? extends Deserializer<?>> valueDeserializerClass;

    private long pollTimeout = DEFAULT_POLL_TIMEOUT;

    /**
     * Creates an instance.
     *
     * @param keyDeserializerClass The class to be used for deserializing the record keys, if {@code null} the
     *            deserializer needs to be provided in the configuration at runtime.
     * @param valueDeserializerClass The class to be used for deserializing the record values, if {@code null} the
     *            deserializer needs to be provided in the configuration at runtime.
     */
    protected KafkaConsumerConfigProperties(final Class<? extends Deserializer<?>> keyDeserializerClass,
            final Class<? extends Deserializer<?>> valueDeserializerClass) {

        this.keyDeserializerClass = keyDeserializerClass;
        this.valueDeserializerClass = valueDeserializerClass;
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
     */
    protected KafkaConsumerConfigProperties(final Class<? extends Deserializer<?>> keyDeserializerClass,
            final Class<? extends Deserializer<?>> valueDeserializerClass,
            final CommonKafkaClientOptions commonOptions,
            final KafkaConsumerOptions options) {
        this.keyDeserializerClass = keyDeserializerClass;
        this.valueDeserializerClass = valueDeserializerClass;

        setCommonClientConfig(ConfigOptionsHelper.toStringValueMap(commonOptions.commonClientConfig()));
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
     * configuration. It includes changes made in {@link #adaptConfiguration(Map)}. The returned map will contain a
     * property {@code client.id} which will be set to a unique value containing the already set client id or
     * alternatively the value set via {@link #setDefaultClientIdPrefix(String)}, the given consumerName and a newly
     * created UUID.
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
     * Subclasses may overwrite this method to set expected configuration values. The default implementation does
     * nothing.
     *
     * @param config The consumer configuration to be adapted.
     */
    protected void adaptConfiguration(final Map<String, String> config) {

    }

    /**
     * Sets the timeout for polling records.
     * <p>
     * The default value of this property is {@value #DEFAULT_POLL_TIMEOUT}.
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
     * The default value of this property is {@value #DEFAULT_POLL_TIMEOUT}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final long getPollTimeout() {
        return pollTimeout;
    }
}
