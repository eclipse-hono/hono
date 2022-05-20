/*
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.producer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaProducerConfigProperties}.
 */
public class KafkaProducerConfigPropertiesTest {

    private KafkaProducerConfigProperties config;

    @BeforeEach
    void setUp() {
        config = new KafkaProducerConfigProperties(StringSerializer.class, StringSerializer.class);
    }

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> config.setProducerConfig(null));
    }

    /**
     * Verifies that properties provided with {@link KafkaProducerConfigProperties#setProducerConfig(Map)} are returned
     * in {@link KafkaProducerConfigProperties#getProducerConfig(String)}.
     */
    @Test
    public void testThatGetProducerConfigReturnsGivenProperties() {
        config.setProducerConfig(Map.of("foo", "bar"));

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link KafkaProducerConfigProperties#setProducerConfig(Map)} and
     * {@link AbstractKafkaConfigProperties#setCommonClientConfig(CommonKafkaClientConfigProperties)} are returned in
     * {@link KafkaProducerConfigProperties#getProducerConfig(String)}, with the producer config properties having
     * precedence.
     */
    @Test
    public void testThatGetProducerConfigReturnsGivenPropertiesWithCommonProperties() {
        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties();
        commonConfig.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));
        config.setCommonClientConfig(commonConfig);
        config.setProducerConfig(Map.of("foo", "bar"));

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("foo")).isEqualTo("bar");
        assertThat(producerConfig.get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link KafkaProducerConfigProperties#isConfigured()} returns false if no configuration has been
     * set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(config.isConfigured()).isFalse();

        config.setProducerConfig(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "kafka"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that the client ID set in the {@link KafkaProducerConfigProperties#getProducerConfig(String)}
     * map conforms to the expected format.
     */
    @Test
    public void testThatCreatedClientIdConformsToExpectedFormat() {
        final String componentUid = "hono-adapter-amqp-7548cc6c66-4qhqh_ed7c6ab9cc27";

        config.setProducerConfig(Map.of("client.id", "configuredId"));
        config.overrideComponentUidUsedForClientId(componentUid);

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("client.id")).matches("configuredId-producerName-" + componentUid + "_\\d+");
    }
}
