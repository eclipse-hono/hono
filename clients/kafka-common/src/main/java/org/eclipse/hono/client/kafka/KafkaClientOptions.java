/**
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

package org.eclipse.hono.client.kafka;

import java.util.Map;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigValue;

/**
 * Common options for configuring Kafka clients.
 */
@ApplicationScoped
public class KafkaClientOptions {

    @Inject
    RawKafkaClientOptions rawKafkaClientOptions;

    /**
     * The properties shared by all types of clients.
     *
     * @return The properties.
     */
    public Map<String, String> commonClientConfig() {
        return toStringValueMap(rawKafkaClientOptions.commonClientConfig());
    }

    /**
     * The properties to use for admin clients.
     *
     * @return The properties.
     */
    public Map<String, String> adminClientConfig() {
        return toStringValueMap(rawKafkaClientOptions.adminClientConfig());
    }

    /**
     * The properties to use for consumers.
     *
     * @return The properties.
     */
    public Map<String, String> consumerConfig() {
        return toStringValueMap(rawKafkaClientOptions.consumerConfig());
    }

    /**
     * The properties to use for producers.
     *
     * @return The properties.
     */
    public Map<String, String> producerConfig() {
        return toStringValueMap(rawKafkaClientOptions.producerConfig());
    }

    private static Map<String, String> toStringValueMap(final Map<String, ConfigValue> config) {
        return config.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }

    /**
     * Container for the Kafka client configuration Maps.
     * <p>
     * This class with its {@link ConfigValue} (instead of String) Map values is needed to support empty String values.
     * The default value converters consider an empty String as a {@code null} value and then throw a
     * {@link java.util.NoSuchElementException}.
     * Using {@link ConfigValue} bypasses usage of the converter and allows access to the original value.
     */
    @ConfigMapping(prefix = "hono.kafka", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
    interface RawKafkaClientOptions {

        /**
         * The properties shared by all types of clients.
         * @return The properties.
         */
        Map<String, ConfigValue> commonClientConfig();

        /**
         * The properties to use for admin clients.
         * @return The properties.
         */
        Map<String, ConfigValue> adminClientConfig();

        /**
         * The properties to use for consumers.
         * @return The properties.
         */
        Map<String, ConfigValue> consumerConfig();

        /**
         * The properties to use for producers.
         * @return The properties.
         */
        Map<String, ConfigValue> producerConfig();
    }
}
