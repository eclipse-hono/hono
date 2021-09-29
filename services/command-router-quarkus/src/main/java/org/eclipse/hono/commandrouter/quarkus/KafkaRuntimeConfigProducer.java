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

package org.eclipse.hono.commandrouter.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.service.util.quarkus.ConfigPropertiesHelper;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Parses Hono's Kafka configuration properties and provides them as beans.
 * <p>
 * This replaces the behavior of {@link io.quarkus.kafka.client.runtime.KafkaRuntimeConfigProducer} from Quarkus' Kafka
 * extension. The dependency also provides some patches that are required to use the Kafka clients in native images with
 * GraalVM.
 */
@ApplicationScoped
public class KafkaRuntimeConfigProducer {

    /**
     * The prefix of the common Kafka configuration.
     */
    public static final String COMMON_CONFIG_PREFIX = "hono.kafka.commonClientConfig";

    /**
     * The prefix of the Kafka producer configuration.
     */
    public static final String PRODUCER_CONFIG_PREFIX = "hono.kafka.producerConfig";

    /**
     * The prefix of the Kafka consumer configuration.
     */
    public static final String CONSUMER_CONFIG_PREFIX = "hono.kafka.consumerConfig";

    private static final String DEFAULT_CLIENT_ID_PREFIX = "cmd-router";

    /**
     * Exposes Kafka producer configuration properties as a bean.
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
        configProperties.setCommonClientConfig(ConfigPropertiesHelper.createPropertiesFromConfig(config, COMMON_CONFIG_PREFIX));
        configProperties.setProducerConfig(ConfigPropertiesHelper.createPropertiesFromConfig(config, PRODUCER_CONFIG_PREFIX));
        configProperties.setDefaultClientIdPrefix(DEFAULT_CLIENT_ID_PREFIX);
        return configProperties;
    }

    /**
     * Exposes Kafka consumer configuration properties as a bean.
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
        configProperties.setCommonClientConfig(ConfigPropertiesHelper.createPropertiesFromConfig(config, COMMON_CONFIG_PREFIX));
        configProperties.setConsumerConfig(ConfigPropertiesHelper.createPropertiesFromConfig(config, CONSUMER_CONFIG_PREFIX));
        configProperties.setDefaultClientIdPrefix(DEFAULT_CLIENT_ID_PREFIX);
        return configProperties;
    }
}
