/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;

/**
 * Configuration properties for Kafka consumers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client.
 *
 * @see <a href="https://kafka.apache.org/documentation/#consumerconfigs">Kafka Consumer Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/telemetry-kafka">Telemetry API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/event-kafka">Event API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">Command &amp; Control API for Kafka Specification</a>
 */
public class KafkaConsumerConfigProperties extends AbstractKafkaConfigProperties {

    /**
     * The default amount of time (milliseconds) to wait for records when polling records.
     */
    public static final long DEFAULT_POLL_TIMEOUT = 100L; // ms

    private Map<String, String> consumerConfig;
    private long pollTimeout = DEFAULT_POLL_TIMEOUT;

    /**
     * Sets the Kafka consumer config properties to be used.
     *
     * @param consumerConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public final void setConsumerConfig(final Map<String, String> consumerConfig) {
        this.consumerConfig = Objects.requireNonNull(consumerConfig);
    }

    /**
     * Checks if a configuration has been set.
     *
     * @return true if configuration is present.
     */
    public final boolean isConfigured() {
        return commonClientConfig != null || consumerConfig != null;
    }

    /**
     * Gets the Kafka consumer configuration to which additional properties were applied. The following properties are
     * set here to the given configuration:
     * <ul>
     * <li>{@code key.deserializer=org.apache.kafka.common.serialization.StringDeserializer}: defines how message keys
     * are deserialized</li>
     * <li>{@code value.deserializer=io.vertx.kafka.client.serialization.BufferDeserializer}: defines how message values
     * are deserialized</li>
     * <li>{@code client.id}=${unique client id}: the client id will be set to a unique value containing the already set
     * client id or alternatively the value set via {@link #setDefaultClientIdPrefix(String)}, the given consumerName
     * and a newly created UUID.</li>
     * </ul>
     * Note: This method should be called for each new consumer, ensuring that a unique client id is used.
     *
     * @param consumerName A name for the consumer to include in the added {@code client.id} property.
     * @return a copy of the consumer configuration with the applied properties or an empty map if neither a consumer
     *         client configuration was set with {@link #setConsumerConfig(Map)} nor common configuration properties were
     *         set with {@link #setCommonClientConfig(Map)}.
     * @throws NullPointerException if consumerName is {@code null}.
     */
    public final Map<String, String> getConsumerConfig(final String consumerName) {
        Objects.requireNonNull(consumerName);

        if (commonClientConfig == null && consumerConfig == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> newConfig = new HashMap<>();
        if (commonClientConfig != null) {
            newConfig.putAll(commonClientConfig);
        }
        if (consumerConfig != null) {
            newConfig.putAll(consumerConfig);
        }

        overrideConfigProperty(newConfig, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        overrideConfigProperty(newConfig, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.vertx.kafka.client.serialization.BufferDeserializer");

        setUniqueClientId(newConfig, consumerName, ConsumerConfig.CLIENT_ID_CONFIG);
        return newConfig;
    }

    /**
     * Sets the timeout for polling records.
     * <p>
     * The default value of this property is {@link #DEFAULT_POLL_TIMEOUT}.
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
     * The default value of this property is {@link #DEFAULT_POLL_TIMEOUT}.
     *
     * @return The maximum number of milliseconds to wait.
     */
    public final long getPollTimeout() {
        return pollTimeout;
    }

}
