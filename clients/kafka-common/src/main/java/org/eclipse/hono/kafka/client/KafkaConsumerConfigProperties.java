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

package org.eclipse.hono.kafka.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration properties for Kafka consumers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client. It only sets a couple of properties that are important for Hono to
 * provide the expected quality of service.
 *
 * @see <a href="https://kafka.apache.org/documentation/#consumerconfigs">Kafka Consumer Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/kafka">Documentation of Hono's Kafka-based APIs</a>
 */
// TODO check link to Hono documentation after the API specs are on master
public class KafkaConsumerConfigProperties {

    private final Logger log = LoggerFactory.getLogger(KafkaConsumerConfigProperties.class);

    private Map<String, String> consumerConfig;
    private String clientId;

    /**
     * Sets the Kafka consumer config properties to be used.
     *
     * @param consumerConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public void setConsumerConfig(final Map<String, String> consumerConfig) {
        this.consumerConfig = Objects.requireNonNull(consumerConfig);
    }

    /**
     * Sets the client ID that is passed to the Kafka server to allow application specific server-side request logging.
     * <p>
     * If the config set in {@link #setConsumerConfig(Map)} already contains a value for key {@code client.id}, that one
     * will be used and the parameter here will be ignored.
     *
     * @param clientId The client ID to set.
     * @throws NullPointerException if the client ID is {@code null}.
     */
    public final void setClientId(final String clientId) {
        this.clientId = Objects.requireNonNull(clientId);
    }

    /**
     * Gets the Kafka consumer configuration to which additional properties were applied. The following properties are
     * set here to the given configuration:
     * <ul>
     * <li>{@code key.deserializer=org.apache.kafka.common.serialization.StringDeserializer}: defines how message keys
     * are deserialized</li>
     * <li>{@code value.deserializer=io.vertx.kafka.client.serialization.BufferDeserializer}: defines how message values
     * are deserialized</li>
     * <li>{@code client.id} if the property is not already present in the configuration and a value has been set with
     * {@link #setClientId(String)}, this value will be taken</li>
     * </ul>
     *
     * @return a copy of the consumer configuration with the applied properties or {@code null} if no consumer
     *         configuration was set with {@link #setConsumerConfig(Map)}.
     */
    public Map<String, String> getConsumerConfig() {

        if (consumerConfig == null) {
            return null;
        }

        final HashMap<String, String> newConfig = new HashMap<>(consumerConfig);

        overrideConsumerConfigProperty(newConfig, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        overrideConsumerConfigProperty(newConfig, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.vertx.kafka.client.serialization.BufferDeserializer");

        if (clientId != null) {
            newConfig.putIfAbsent(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        }

        return newConfig;
    }

    private void overrideConsumerConfigProperty(final Map<String, String> config, final String key,
            final String value) {

        log.trace("Setting Kafka consumer config property [{}={}]", key, value);
        final Object oldValue = config.put(key, value);
        if (oldValue != null) {
            log.debug("Provided Kafka consumer configuration contains property [{}={}], changing it to [{}]", key,
                    oldValue, value);
        }

    }

}
