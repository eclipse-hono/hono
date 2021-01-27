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

package org.eclipse.hono.client.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaConsumerConfigProperties}.
 */
public class KafkaConsumerConfigPropertiesTest {

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaConsumerConfigProperties().setConsumerConfig(null));
    }

    /**
     * Verifies that trying to set a {@code null} client ID throws a {@link NullPointerException}.
     */
    @Test
    public void testThatClientIdCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaConsumerConfigProperties().setDefaultClientIdPrefix(null));
    }

    /**
     * Verifies that trying to set a negative poll timeout throws an {@link IllegalArgumentException}.
     */
    @Test
    public void testThatPollTimeoutCanNotBeSetToNull() {
        assertThrows(IllegalArgumentException.class, () -> new KafkaConsumerConfigProperties().setPollTimeout(-1L));
    }

    /**
     * Verifies that properties provided with {@link KafkaConsumerConfigProperties#setConsumerConfig(Map)} are returned
     * in {@link KafkaConsumerConfigProperties#getConsumerConfig()}.
     */
    @Test
    public void testThatGetConsumerConfigReturnsGivenProperties() {
        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        config.setConsumerConfig(Collections.singletonMap("foo", "bar"));

        assertThat(config.getConsumerConfig().get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link KafkaConsumerConfigProperties#setConsumerConfig(Map)} and
     * {@link org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties#setCommonClientConfig(Map)} are returned
     * in {@link KafkaConsumerConfigProperties#getConsumerConfig()}, with the consumer config properties having
     * precedence.
     */
    @Test
    public void testThatGetConsumerConfigReturnsGivenPropertiesWithCommonProperties() {
        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        config.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));
        config.setConsumerConfig(Collections.singletonMap("foo", "bar"));

        assertThat(config.getConsumerConfig().get("foo")).isEqualTo("bar");
        assertThat(config.getConsumerConfig().get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link KafkaConsumerConfigProperties#isConfigured()} returns false if no configuration has been
     * set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(new KafkaConsumerConfigProperties().isConfigured()).isFalse();

        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        config.setConsumerConfig(Collections.singletonMap("foo", "bar"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that properties returned in {@link KafkaConsumerConfigProperties#getConsumerConfig()} ()} contain
     * the predefined entries, overriding any corresponding properties given in {@link KafkaConsumerConfigProperties#setConsumerConfig(Map)}.
     */
    @Test
    public void testThatGetConsumerConfigReturnsAdaptedConfig() {

        final Map<String, String> properties = new HashMap<>();
        properties.put("key.deserializer", "foo");
        properties.put("value.deserializer", "bar");
        properties.put("enable.idempotence", "baz");

        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        config.setConsumerConfig(properties);

        final Map<String, String> consumerConfig = config.getConsumerConfig();

        assertThat(consumerConfig.get("key.deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        assertThat(consumerConfig.get("value.deserializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.BufferDeserializer");
    }

    /**
     * Verifies that the client ID set with {@link KafkaConsumerConfigProperties#setDefaultClientIdPrefix(String)} is applied when it
     * is NOT present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        config.setConsumerConfig(Collections.emptyMap());
        config.setDefaultClientIdPrefix(clientId);

        assertThat(config.getConsumerConfig().get("client.id")).isEqualTo(clientId);
    }

    /**
     * Verifies that the client ID set with {@link KafkaConsumerConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied
     * when it is present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {
        final String userProvidedClientId = "custom-client";

        final KafkaConsumerConfigProperties config = new KafkaConsumerConfigProperties();
        config.setConsumerConfig(Collections.singletonMap("client.id", userProvidedClientId));
        config.setDefaultClientIdPrefix("other-client");

        assertThat(config.getConsumerConfig().get("client.id")).isEqualTo(userProvidedClientId);
    }

}
