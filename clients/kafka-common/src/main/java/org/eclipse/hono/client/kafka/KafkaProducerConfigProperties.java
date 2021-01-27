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

package org.eclipse.hono.client.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * Configuration properties for Kafka producers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client. It only sets a couple of properties that are important for Hono to
 * provide the expected quality of service.
 *
 * @see <a href="https://kafka.apache.org/documentation/#producerconfigs">Kafka Producer Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/telemetry-kafka">Telemetry API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/event-kafka">Event API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">Command &amp; Control API for Kafka Specification</a>
 */
public class KafkaProducerConfigProperties extends AbstractKafkaConfigProperties {

    private Map<String, String> producerConfig;

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
     * @return true if configuration is present.
     */
    public final boolean isConfigured() {
        return commonClientConfig != null || producerConfig != null;
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
     * <li>{@code client.id}=${unique client id}: the client id will be set to a unique value containing the already set
     * client id or alternatively the value set via {@link #setDefaultClientIdPrefix(String)}, the given producerName
     * and a newly created UUID.</li>
     * </ul>
     * Note: This method should be called for each new producer, ensuring that a unique client id is used.
     *
     * @param producerName A name for the producer to include in the added {@code client.id} property.
     * @return a copy of the producer configuration with the applied properties or an empty map if neither a producer
     *         client configuration was set with {@link #setProducerConfig(Map)} nor common configuration properties were
     *         set with {@link #setCommonClientConfig(Map)}.
     * @throws NullPointerException if producerName is {@code null}.
     * @see <a href="https://kafka.apache.org/documentation/#enable.idempotence">The Kafka documentation -
     *      "Producer Configs" - enable.idempotence</a>
     */
    public final Map<String, String> getProducerConfig(final String producerName) {
        Objects.requireNonNull(producerName);

        if (commonClientConfig == null && producerConfig == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> newConfig = new HashMap<>();
        if (commonClientConfig != null) {
            newConfig.putAll(commonClientConfig);
        }
        if (producerConfig != null) {
            newConfig.putAll(producerConfig);
        }

        overrideConfigProperty(newConfig, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");

        overrideConfigProperty(newConfig, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.vertx.kafka.client.serialization.BufferSerializer");

        overrideConfigProperty(newConfig, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        setUniqueClientId(newConfig, producerName, ProducerConfig.CLIENT_ID_CONFIG);
        return newConfig;
    }

}
