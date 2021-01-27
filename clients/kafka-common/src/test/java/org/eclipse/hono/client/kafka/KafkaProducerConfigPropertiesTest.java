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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaProducerConfigProperties}.
 */
public class KafkaProducerConfigPropertiesTest {

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaProducerConfigProperties().setProducerConfig(null));
    }

    /**
     * Verifies that trying to set a {@code null} client ID throws a {@link NullPointerException}.
     */
    @Test
    public void testThatClientIdCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaProducerConfigProperties().setDefaultClientIdPrefix(null));
    }

    /**
     * Verifies that properties provided with {@link KafkaProducerConfigProperties#setProducerConfig(Map)} are returned
     * in {@link KafkaProducerConfigProperties#getProducerConfig()}.
     */
    @Test
    public void testThatGetProducerConfigReturnsGivenProperties() {
        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setProducerConfig(Collections.singletonMap("foo", "bar"));

        assertThat(config.getProducerConfig().get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link KafkaProducerConfigProperties#setProducerConfig(Map)} and
     * {@link org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties#setCommonClientConfig(Map)} are returned
     * in {@link KafkaProducerConfigProperties#getProducerConfig()}, with the producer config properties having
     * precedence.
     */
    @Test
    public void testThatGetProducerConfigReturnsGivenPropertiesWithCommonProperties() {
        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));
        config.setProducerConfig(Collections.singletonMap("foo", "bar"));

        assertThat(config.getProducerConfig().get("foo")).isEqualTo("bar");
        assertThat(config.getProducerConfig().get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link KafkaProducerConfigProperties#isConfigured()} returns false if no configuration has been
     * set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(new KafkaProducerConfigProperties().isConfigured()).isFalse();

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setProducerConfig(Collections.singletonMap("foo", "bar"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that properties returned in {@link KafkaProducerConfigProperties#getProducerConfig()} contain
     * the predefined entries, overriding any corresponding properties given in {@link KafkaProducerConfigProperties#setProducerConfig(Map)}.
     */
    @Test
    public void testThatGetProducerConfigReturnsAdaptedConfig() {

        final Map<String, String> properties = new HashMap<>();
        properties.put("key.serializer", "foo");
        properties.put("value.serializer", "bar");
        properties.put("enable.idempotence", "baz");

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setProducerConfig(properties);

        final Map<String, String> producerConfig = config.getProducerConfig();

        assertThat(producerConfig.get("key.serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(producerConfig.get("value.serializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.BufferSerializer");
        assertThat(producerConfig.get("enable.idempotence")).isEqualTo("true");
    }

    /**
     * Verifies that the client ID set with {@link KafkaProducerConfigProperties#setDefaultClientIdPrefix(String)} is applied when it
     * is NOT present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setProducerConfig(Collections.emptyMap());
        config.setDefaultClientIdPrefix(clientId);

        assertThat(config.getProducerConfig().get("client.id")).isEqualTo(clientId);
    }

    /**
     * Verifies that the client ID set with {@link KafkaProducerConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied
     * when it is present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {
        final String userProvidedClientId = "custom-client";

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setProducerConfig(Collections.singletonMap("client.id", userProvidedClientId));
        config.setDefaultClientIdPrefix("other-client");

        assertThat(config.getProducerConfig().get("client.id")).isEqualTo(userProvidedClientId);
    }

}
