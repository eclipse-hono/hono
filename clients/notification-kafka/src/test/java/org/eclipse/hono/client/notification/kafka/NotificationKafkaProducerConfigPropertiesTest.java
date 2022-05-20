/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import org.apache.kafka.clients.CommonClientConfigs;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.CommonKafkaClientConfigProperties;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link NotificationKafkaProducerConfigProperties}.
 */
public class NotificationKafkaProducerConfigPropertiesTest {

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class,
                () -> new NotificationKafkaProducerConfigProperties().setProducerConfig(null));
    }

    /**
     * Verifies that properties provided with {@link NotificationKafkaProducerConfigProperties#setProducerConfig(Map)}
     * are returned in {@link NotificationKafkaProducerConfigProperties#getProducerConfig(String)}.
     */
    @Test
    public void testThatGetProducerConfigReturnsGivenProperties() {
        final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();
        config.setProducerConfig(Map.of("foo", "bar"));

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link NotificationKafkaProducerConfigProperties#setProducerConfig(Map)}
     * and {@link AbstractKafkaConfigProperties#setCommonClientConfig(CommonKafkaClientConfigProperties)} are returned
     * in {@link NotificationKafkaProducerConfigProperties#getProducerConfig(String)}, with the producer config
     * properties having precedence.
     */
    @Test
    public void testThatGetProducerConfigReturnsGivenPropertiesWithCommonProperties() {
        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties();
        commonConfig.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));

        final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();
        config.setCommonClientConfig(commonConfig);
        config.setProducerConfig(Map.of("foo", "bar"));

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("foo")).isEqualTo("bar");
        assertThat(producerConfig.get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link NotificationKafkaProducerConfigProperties#isConfigured()} returns false if no configuration
     * has been set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(new NotificationKafkaProducerConfigProperties().isConfigured()).isFalse();

        final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();
        config.setProducerConfig(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "kafka"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that properties returned in {@link NotificationKafkaProducerConfigProperties#getProducerConfig(String)}
     * contain the predefined entries, overriding any corresponding properties given in
     * {@link NotificationKafkaProducerConfigProperties#setProducerConfig(Map)}.
     */
    @Test
    public void testThatGetProducerConfigReturnsAdaptedConfig() {

        final Map<String, String> properties = new HashMap<>();
        properties.put("key.serializer", "foo");
        properties.put("value.serializer", "bar");
        properties.put("enable.idempotence", "baz");

        final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();
        config.setProducerConfig(properties);

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");

        assertThat(producerConfig.get("key.serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(producerConfig.get("value.serializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.JsonObjectSerializer");
        assertThat(producerConfig.get("enable.idempotence")).isEqualTo("true");
    }

    /**
     * Verifies that the client ID set in the {@link NotificationKafkaProducerConfigProperties#getProducerConfig(String)}
     * map conforms to the expected format.
     */
    @Test
    public void testThatCreatedClientIdConformsToExpectedFormat() {
        final String componentUid = "hono-adapter-amqp-7548cc6c66-4qhqh_ed7c6ab9cc27";

        final NotificationKafkaProducerConfigProperties config = new NotificationKafkaProducerConfigProperties();
        config.setProducerConfig(Map.of("client.id", "configuredId"));
        config.overrideComponentUidUsedForClientId(componentUid);

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("client.id")).matches("^configuredId-producerName-" + componentUid + "_\\d+");
    }
}
