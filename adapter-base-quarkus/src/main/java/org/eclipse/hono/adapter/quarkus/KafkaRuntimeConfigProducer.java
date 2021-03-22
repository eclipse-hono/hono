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

package org.eclipse.hono.adapter.quarkus;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Hono's Kafka configuration properties and provides them as beans.
 * <p>
 * This replaces the behavior of {@code io.quarkus.kafka.client.runtime.KafkaRuntimeConfigProducer} from Quarkus' Kafka
 * extension. The dependency also provides some patches that are required to use the Kafka clients in native images with
 * GraalVM.
 */
@ApplicationScoped
public class KafkaRuntimeConfigProducer {

    /**
     * The prefix for Hono's common Kafka configuration.
     */
    public static final String COMMON_CONFIG_PREFIX = "hono.kafka.commonClientConfig";

    /**
     * The prefix for Hono's Kafka producer configuration.
     */
    public static final String PRODUCER_CONFIG_PREFIX = "hono.kafka.producerConfig";

    /**
     * The prefix for Hono's Kafka consumer configuration.
     */
    public static final String CONSUMER_CONFIG_PREFIX = "hono.kafka.consumerConfig";

    /**
     * The prefix for Hono's Kafka admin client configuration.
     */
    public static final String ADMIN_CLIENT_CONFIG_PREFIX = "hono.kafka.adminClientConfig";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaRuntimeConfigProducer.class);

    /**
     * Exposes Hono's Kafka producer configuration properties as a bean.
     * <p>
     * The configuration properties are derived from the provided configuration with the prefixes
     * {@link #COMMON_CONFIG_PREFIX} and {@link #PRODUCER_CONFIG_PREFIX}.
     *
     * @return The configuration properties.
     */
    @Produces
    @Singleton
    public KafkaProducerConfigProperties createKafkaProducerRuntimeConfig() {
        final Config config = ConfigProvider.getConfig();

        final KafkaProducerConfigProperties configProperties = new KafkaProducerConfigProperties();
        configProperties.setCommonClientConfig(createPropertiesFromConfig(config, COMMON_CONFIG_PREFIX));
        configProperties.setProducerConfig(createPropertiesFromConfig(config, PRODUCER_CONFIG_PREFIX));
        return configProperties;
    }

    /**
     * Exposes Hono's Kafka consumer configuration properties as a bean.
     * <p>
     * The configuration properties are derived from the provided configuration with the prefixes
     * {@link #COMMON_CONFIG_PREFIX} and {@link #CONSUMER_CONFIG_PREFIX}.
     *
     * @return The configuration properties.
     */
    @Produces
    @Singleton
    public KafkaConsumerConfigProperties createKafkaConsumerRuntimeConfig() {
        final Config config = ConfigProvider.getConfig();

        final KafkaConsumerConfigProperties configProperties = new KafkaConsumerConfigProperties();
        configProperties.setCommonClientConfig(createPropertiesFromConfig(config, COMMON_CONFIG_PREFIX));
        configProperties.setConsumerConfig(createPropertiesFromConfig(config, CONSUMER_CONFIG_PREFIX));
        return configProperties;
    }

    /**
     * Exposes Hono's Kafka admin client configuration properties as a bean.
     * <p>
     * The configuration properties are derived from the provided configuration with the prefixes
     * {@link #COMMON_CONFIG_PREFIX} and {@link #ADMIN_CLIENT_CONFIG_PREFIX}.
     *
     * @return The configuration properties.
     */
    @Produces
    @Singleton
    public KafkaAdminClientConfigProperties createKafkaAdminClientRuntimeConfig() {
        final Config config = ConfigProvider.getConfig();

        final KafkaAdminClientConfigProperties configProperties = new KafkaAdminClientConfigProperties();
        configProperties.setCommonClientConfig(createPropertiesFromConfig(config, COMMON_CONFIG_PREFIX));
        configProperties.setAdminClientConfig(createPropertiesFromConfig(config, ADMIN_CLIENT_CONFIG_PREFIX));
        return configProperties;
    }

    private Map<String, String> createPropertiesFromConfig(final Config config, final String configPrefix) {
        final Map<String, String> properties = new HashMap<>();

        LOG.trace("config properties picked up with names: {}", config.getPropertyNames());

        StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .filter(name -> name.startsWith(configPrefix))
                .distinct()
                .sorted()
                .forEach(name -> {
                    final String key = name.substring(configPrefix.length() + 1)
                            .toLowerCase()
                            .replaceAll("\"", "")
                            .replaceAll("[^a-z0-9.]", ".");
                    final String value = config.getOptionalValue(name, String.class).orElse("");
                    properties.put(key, value);
                });

        LOG.debug("Kafka config properties: {}", properties);
        return properties;
    }

}
