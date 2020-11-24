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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration properties for Kafka producers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client. It only sets a couple of properties that are important for Hono to
 * provide the expected quality of service.
 *
 * @see <a href="https://kafka.apache.org/documentation/#producerconfigs">Kafka Producer Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/kafka">Documentation of Hono's Kafka-based APIs</a>
 */
// TODO check link to Hono documentation after the API specs are on master
public class KafkaProducerConfigProperties {

    private final Logger log = LoggerFactory.getLogger(KafkaProducerConfigProperties.class);

    private Map<String, String> producerConfig;
    private String clientId;

    /**
     * Sets the Kafka producer config properties to be used.
     *
     * @param producerConfig The config properties.
     * @throws NullPointerException if the config is {@code null}.
     */
    public void setProducerConfig(final Map<String, String> producerConfig) {
        this.producerConfig = Objects.requireNonNull(producerConfig);
    }

    /**
     * Sets the client ID that is passed to the Kafka server to allow application specific server-side request logging.
     * <p>
     * If the config set in {@link #setProducerConfig(Map)} already contains a value for key {@code client.id}, that one
     * will be used and the parameter here will be ignored.
     *
     * @param clientId The client ID to set.
     * @throws NullPointerException if the client ID is {@code null}.
     */
    public final void setClientId(final String clientId) {
        this.clientId = Objects.requireNonNull(clientId);
    }

    /**
     * Gets the Kafka producer configuration to which additional properties were applied. The following properties are
     * set here to the given configuration:
     * <ul>
     * <li>{@code enable.idempotence=true}: enables idempotent producer behavior</li>
     * <li>{@code key.serializer=org.apache.kafka.common.serialization.StringSerializer}: defines how message keys are
     * serialized</li>
     * <li>{@code value.serializer=io.vertx.kafka.client.serialization.BufferSerializer}: defines how message values are
     * serialized</li>
     *
     * <li>{@code client.id} if the property is not already present in the configuration and a value has been set with
     * {@link #setClientId(String)}, this value will be taken</li>
     * </ul>
     *
     * @return a copy of the producer configuration with the applied properties or an empty map if no producer
     *         configuration was set with {@link #setProducerConfig(Map)}.
     * @see <a href="https://kafka.apache.org/documentation/#enable.idempotence">The Kafka documentation -
     *      "Producer Configs" - enable.idempotence</a>
     */
    public Map<String, String> getProducerConfig() {

        if (producerConfig == null) {
            return Collections.emptyMap();
        }

        final HashMap<String, String> newConfig = new HashMap<>(producerConfig);

        overrideProducerConfigProperty(newConfig, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");

        overrideProducerConfigProperty(newConfig, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.vertx.kafka.client.serialization.BufferSerializer");

        overrideProducerConfigProperty(newConfig, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        if (clientId != null) {
            newConfig.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        }

        return newConfig;
    }

    private void overrideProducerConfigProperty(final Map<String, String> config, final String key,
            final String value) {

        log.trace("Setting Kafka producer config property [{}={}]", key, value);
        final Object oldValue = config.put(key, value);
        if (oldValue != null) {
            log.debug("Provided Kafka producer configuration contains property [{}={}], changing it to [{}]", key,
                    oldValue, value);
        }

    }

}
