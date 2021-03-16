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

import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.client.kafka.KafkaConfigProperties;

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
public class KafkaConsumerConfigProperties extends KafkaConfigProperties {

    /**
     * The default amount of time (milliseconds) to wait for records when polling records.
     */
    public static final long DEFAULT_POLL_TIMEOUT = 100L; // ms

    private long pollTimeout = DEFAULT_POLL_TIMEOUT;

    /**
     * {@inheritDoc}
     * <p>
     * Adds the following properties:
     * <ul>
     * <li>{@code key.serializer=org.apache.kafka.common.serialization.StringSerializer}: defines how message keys are
     * serialized</li>
     * <li>{@code value.serializer=io.vertx.kafka.client.serialization.BufferSerializer}: defines how message values are
     * serialized</li>
     * </ul>
     */
    @Override
    protected void postProcessConfiguration(final Map<String, String> configuration) {
        overrideConfigProperty(configuration, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        overrideConfigProperty(configuration, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.vertx.kafka.client.serialization.BufferDeserializer");
    }

    /**
     * Gets the Kafka consumer configuration to which additional properties were applied. The following properties are
     * set here to the given configuration:
     * <ul>
     * <li>{@code client.id}=${unique client id}: the client id will be set to a unique value containing the already set
     * client id or alternatively the value set via {@link #setDefaultClientIdPrefix(String)}, the given consumerName
     * and a newly created UUID.</li>
     * </ul>
     * Note: This method should be called for each new consumer, ensuring that a unique client id is used.
     *
     * @param consumerName A name for the consumer to include in the added {@code client.id} property.
     * @return The consumer configuration properties.
     * @throws NullPointerException if consumerName is {@code null}.
     */
    public final Map<String, String> getConsumerConfig(final String consumerName) {
        Objects.requireNonNull(consumerName);

        final Map<String, String> newConfig = getConfig();
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
