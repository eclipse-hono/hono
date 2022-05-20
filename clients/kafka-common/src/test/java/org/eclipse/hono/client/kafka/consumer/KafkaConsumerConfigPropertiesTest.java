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

package org.eclipse.hono.client.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaConsumerConfigProperties}.
 */
public class KafkaConsumerConfigPropertiesTest {

    private KafkaConsumerConfigProperties config;

    @BeforeEach
    void setUp() {
        config = new KafkaConsumerConfigProperties(StringDeserializer.class, StringDeserializer.class);
    }

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class,
                () -> config.setConsumerConfig(null));
    }

    /**
     * Verifies that trying to set a negative poll timeout throws an {@link IllegalArgumentException}.
     */
    @Test
    public void testThatPollTimeoutCanNotBeSetToNull() {
        assertThrows(IllegalArgumentException.class,
                () -> config.setPollTimeout(-1L));
    }

    /**
     * Verifies that properties provided with {@link KafkaConsumerConfigProperties#setConsumerConfig(Map)} are returned
     * in {@link KafkaConsumerConfigProperties#getConsumerConfig(String)}.
     */
    @Test
    public void testThatGetConsumerConfigReturnsGivenProperties() {
        config.setConsumerConfig(Map.of("foo", "bar"));

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link KafkaConsumerConfigProperties#setConsumerConfig(Map)} and
     * {@link AbstractKafkaConfigProperties#setCommonClientConfig(CommonKafkaClientConfigProperties)} are returned in
     * {@link KafkaConsumerConfigProperties#getConsumerConfig(String)}, with the consumer config properties having
     * precedence.
     */
    @Test
    public void testThatGetConsumerConfigReturnsGivenPropertiesWithCommonProperties() {
        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties();
        commonConfig.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));
        config.setCommonClientConfig(commonConfig);
        config.setConsumerConfig(Map.of("foo", "bar"));

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("foo")).isEqualTo("bar");
        assertThat(consumerConfig.get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link KafkaConsumerConfigProperties#isConfigured()} returns false if no configuration has been
     * set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(config.isConfigured()).isFalse();

        config.setConsumerConfig(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "kafka"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that the client ID set in the {@link KafkaConsumerConfigProperties#getConsumerConfig(String)} map
     * conforms to the expected format.
     */
    @Test
    public void testThatCreatedClientIdConformsToExpectedFormat() {
        final String componentUid = "hono-adapter-amqp-7548cc6c66-4qhqh_ed7c6ab9cc27";

        config.setConsumerConfig(Map.of("client.id", "configuredId"));
        config.overrideComponentUidUsedForClientId(componentUid);

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("client.id")).matches("configuredId-consumerName-" + componentUid + "_\\d+");
    }

}
