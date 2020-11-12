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

package org.eclipse.hono.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration properties for Kafka producers.
 * <p>
 * This class is intended to be as agnostic to the provided properties as possible in order to be forward-compatible
 * with changes in new versions of the Kafka client. It only sets a couple of properties which are important for Hono to
 * provide the expected quality of service.
 *
 * @see <a href="https://kafka.apache.org/documentation/#producerconfigs">Kafka Producer Config</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/kafka">Documentation of Hono's Kafka-based APIs</a>
 */
// TODO check link to Hono documentation after the API specs are on master
public class KafkaProducerConfigProperties {

    private final Logger log = LoggerFactory.getLogger(KafkaProducerConfigProperties.class);

    private Map<String, String> producerConfig;
    private String clientId = null;

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
     * Gets the client ID that is passed to the Kafka server to allow application specific server-side request logging.
     * <p>
     * If the config set in {@link #setProducerConfig(Map)} already contains a value for key {@code client.id}, that
     * will be used instead of this property here.
     *
     * @return The client ID or {@code null} if no client ID has been set.
     */
    public final String getClientId() {
        return clientId;
    }

    /**
     * Sets the client ID that is passed to the Kafka server to allow application specific server-side request logging.
     * <p>
     * If the config set in {@link #setProducerConfig(Map)} already contains a value for key {@code client.id}, that
     * will be used and the parameter here will be ignored.
     *
     * @param clientId The client ID to set.
     */
    public final void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    /**
     * Returns the Kafka producer configuration with additional properties applied to enable idempotence, by setting
     * {@code enable.idempotence=true}. This ensures that message order is maintained while enabling retries and
     * "EXACTLY ONCE delivery semantics. It allows <code>max.in.flight.requests.per.connection</code> to be
     * <code>5</code> (and provides therefore a better performance than "AT LEAST ONCE" delivery semantics provided by
     * setting <code>acks=all</code> and <code>max.in.flight.requests.per.connection=1</code>).
     *
     * @return a copy of the producer configuration with the properties applied or {@code null} if no producer config
     *         has been set with{@link #setProducerConfig(Map)}.
     * @see <a href="https://kafka.apache.org/documentation/#enable.idempotence">The Kafka documentation - section
     *      "Producer Configs"</a>
     */
    public Map<String, String> getAtLeastOnceConfig() {

        if (producerConfig == null) {
            return null;
        }

        final Map<String, String> standardConfig = getStandardConfiguration();

        overrideProducerConfigProperty(standardConfig, "enable.idempotence", "true");

        return standardConfig;
    }

    /**
     * Returns the Kafka producer configuration with additional properties applied to enable "AT MOST ONCE" delivery
     * semantics with guaranteed message order.
     * <p>
     * The result contains the following properties:
     * <ul>
     * <li><code>acks=0</code> - enables "fire-and-forget" semantics: the producer will not wait for any
     * acknowledgments</li>
     * <li><code>retries=0</code> - the producer will not retry failed sends (which the client won't notice anyway
     * without receiving acknowledgements)</li>
     * </ul>
     *
     * @return a copy of the producer configuration with the properties applied or {@code null} if no producer config
     *         has been set with{@link #setProducerConfig(Map)}.
     * @see <a href="https://kafka.apache.org/documentation/#enable.idempotence">The Kafka documentation - section
     *      "Producer Configs"</a>
     */
    public Map<String, String> getAtMostOnceConfig() {

        if (producerConfig == null) {
            return null;
        }

        final Map<String, String> standardConfig = getStandardConfiguration();

        overrideProducerConfigProperty(standardConfig, "acks", "0");
        overrideProducerConfigProperty(standardConfig, "retries", "0");

        return standardConfig;
    }

    private Map<String, String> getStandardConfiguration() {

        final HashMap<String, String> newConfig = new HashMap<>(producerConfig);

        overrideProducerConfigProperty(newConfig, "key.serializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        overrideProducerConfigProperty(newConfig, "value.serializer",
                "io.vertx.kafka.client.serialization.BufferSerializer");

        if (clientId != null) {
            newConfig.putIfAbsent("client.id", clientId);
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
