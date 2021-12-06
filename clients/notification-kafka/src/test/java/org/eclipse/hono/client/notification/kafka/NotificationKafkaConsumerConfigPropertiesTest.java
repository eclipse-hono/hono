/*
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

package org.eclipse.hono.client.notification.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link NotificationKafkaConsumerConfigProperties}.
 */
public class NotificationKafkaConsumerConfigPropertiesTest {

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new NotificationKafkaConsumerConfigProperties().setConsumerConfig(null));
    }

    /**
     * Verifies that trying to set a {@code null} client ID throws a {@link NullPointerException}.
     */
    @Test
    public void testThatClientIdCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new NotificationKafkaConsumerConfigProperties().setDefaultClientIdPrefix(null));
    }

    /**
     * Verifies that trying to set a negative poll timeout throws an {@link IllegalArgumentException}.
     */
    @Test
    public void testThatPollTimeoutCanNotBeSetToNull() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationKafkaConsumerConfigProperties().setPollTimeout(-1L));
    }

    /**
     * Verifies that properties provided with {@link NotificationKafkaConsumerConfigProperties#setConsumerConfig(Map)}
     * are returned in {@link NotificationKafkaConsumerConfigProperties#getConsumerConfig(String)}.
     */
    @Test
    public void testThatGetConsumerConfigReturnsGivenProperties() {
        final NotificationKafkaConsumerConfigProperties config = new NotificationKafkaConsumerConfigProperties();
        config.setConsumerConfig(Map.of("foo", "bar"));

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link NotificationKafkaConsumerConfigProperties#setConsumerConfig(Map)}
     * and {@link AbstractKafkaConfigProperties#setCommonClientConfig(CommonKafkaClientConfigProperties)} are returned
     * in {@link NotificationKafkaConsumerConfigProperties#getConsumerConfig(String)}, with the consumer config
     * properties having precedence.
     */
    @Test
    public void testThatGetConsumerConfigReturnsGivenPropertiesWithCommonProperties() {
        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties();
        commonConfig.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));

        final NotificationKafkaConsumerConfigProperties config = new NotificationKafkaConsumerConfigProperties();
        config.setCommonClientConfig(commonConfig);
        config.setConsumerConfig(Map.of("foo", "bar"));

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("foo")).isEqualTo("bar");
        assertThat(consumerConfig.get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link NotificationKafkaConsumerConfigProperties#isConfigured()} returns false if no configuration
     * has been set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(new NotificationKafkaConsumerConfigProperties().isConfigured()).isFalse();

        final NotificationKafkaConsumerConfigProperties config = new NotificationKafkaConsumerConfigProperties();
        config.setConsumerConfig(Map.of(AbstractKafkaConfigProperties.PROPERTY_BOOTSTRAP_SERVERS, "kafka"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that properties returned in {@link NotificationKafkaConsumerConfigProperties#getConsumerConfig(String)}
     * ()} contain the predefined entries, overriding any corresponding properties given in
     * {@link NotificationKafkaConsumerConfigProperties#setConsumerConfig(Map)}.
     */
    @Test
    public void testThatGetConsumerConfigReturnsAdaptedConfig() {

        final Map<String, String> properties = new HashMap<>();
        properties.put("key.deserializer", "foo");
        properties.put("value.deserializer", "bar");
        properties.put("enable.idempotence", "baz");

        final NotificationKafkaConsumerConfigProperties config = new NotificationKafkaConsumerConfigProperties();
        config.setConsumerConfig(properties);

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");

        assertThat(consumerConfig.get("key.deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        assertThat(consumerConfig.get("value.deserializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.JsonObjectDeserializer");
    }

    /**
     * Verifies that the client ID set with
     * {@link NotificationKafkaConsumerConfigProperties#setDefaultClientIdPrefix(String)} is applied when it is NOT
     * present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final NotificationKafkaConsumerConfigProperties config = new NotificationKafkaConsumerConfigProperties();
        config.setConsumerConfig(Map.of());
        config.setDefaultClientIdPrefix(clientId);

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("client.id")).startsWith(clientId + "-consumerName-");
    }

    /**
     * Verifies that the client ID set with
     * {@link NotificationKafkaConsumerConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied when it is
     * present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {
        final String userProvidedClientId = "custom-client";

        final NotificationKafkaConsumerConfigProperties config = new NotificationKafkaConsumerConfigProperties();
        config.setConsumerConfig(Map.of("client.id", userProvidedClientId));
        config.setDefaultClientIdPrefix("other-client");

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("client.id")).startsWith(userProvidedClientId + "-consumerName-");
    }

}
