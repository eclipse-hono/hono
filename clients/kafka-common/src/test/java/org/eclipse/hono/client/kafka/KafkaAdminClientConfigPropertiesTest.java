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

package org.eclipse.hono.client.kafka;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaAdminClientConfigProperties}.
 */
public class KafkaAdminClientConfigPropertiesTest {

    /**
     * Verifies that trying to set a {@code null} config throws a {@link NullPointerException}.
     */
    @Test
    public void testThatConfigCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaAdminClientConfigProperties().setAdminClientConfig(null));
    }

    /**
     * Verifies that properties provided with {@link KafkaAdminClientConfigProperties#setAdminClientConfig(Map)} are returned
     * in {@link KafkaAdminClientConfigProperties#getAdminClientConfig(String)}.
     */
    @Test
    public void testThatGetAdminClientConfigReturnsGivenProperties() {
        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setAdminClientConfig(Map.of("foo", "bar"));

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("foo")).isEqualTo("bar");
    }

    /**
     * Verifies that properties provided with {@link KafkaAdminClientConfigProperties#setAdminClientConfig(Map)} and
     * {@link AbstractKafkaConfigProperties#setCommonClientConfig(CommonKafkaClientConfigProperties)} are returned in
     * {@link KafkaAdminClientConfigProperties#getAdminClientConfig(String)}, with the admin client config properties
     * having precedence.
     */
    @Test
    public void testThatGetAdminClientConfigReturnsGivenPropertiesWithCommonProperties() {
        final CommonKafkaClientConfigProperties commonConfig = new CommonKafkaClientConfigProperties();
        commonConfig.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setCommonClientConfig(commonConfig);
        config.setAdminClientConfig(Map.of("foo", "bar"));

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("foo")).isEqualTo("bar");
        assertThat(adminClientConfig.get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that {@link KafkaAdminClientConfigProperties#isConfigured()} returns false if no configuration has been
     * set, and true otherwise.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(new KafkaAdminClientConfigProperties().isConfigured()).isFalse();

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setAdminClientConfig(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "kafka"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that the client ID set in the {@link KafkaAdminClientConfigProperties#getAdminClientConfig(String)} map
     * conforms to the expected format.
     */
    @Test
    public void testThatCreatedClientIdConformsToExpectedFormat() {
        final String componentUid = "hono-adapter-amqp-7548cc6c66-4qhqh_ed7c6ab9cc27";

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setAdminClientConfig(Map.of("client.id", "configuredId"));
        config.overrideComponentUidUsedForClientId(componentUid);

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("client.id")).matches("configuredId-adminClientName-" + componentUid + "_\\d+");
    }
}
