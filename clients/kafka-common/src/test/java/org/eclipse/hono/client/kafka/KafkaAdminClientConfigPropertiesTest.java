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

package org.eclipse.hono.client.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

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
     * Verifies that trying to set a {@code null} client ID throws a {@link NullPointerException}.
     */
    @Test
    public void testThatClientIdCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaAdminClientConfigProperties().setDefaultClientIdPrefix(null));
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
     * {@link AbstractKafkaConfigProperties#setCommonClientConfig(Map)} are returned
     * in {@link KafkaAdminClientConfigProperties#getAdminClientConfig(String)}, with the admin client config properties having
     * precedence.
     */
    @Test
    public void testThatGetAdminClientConfigReturnsGivenPropertiesWithCommonProperties() {
        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setCommonClientConfig(Map.of("foo", "toBeOverridden", "common", "commonValue"));
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
        config.setAdminClientConfig(Map.of(AbstractKafkaConfigProperties.PROPERTY_BOOTSTRAP_SERVERS, "kafka"));
        assertThat(config.isConfigured()).isTrue();
    }

    /**
     * Verifies that the client ID set with {@link KafkaAdminClientConfigProperties#setDefaultClientIdPrefix(String)} is applied when it
     * is NOT present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setAdminClientConfig(Map.of());
        config.setDefaultClientIdPrefix(clientId);

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("client.id")).startsWith(clientId + "-adminClientName-");
    }

    /**
     * Verifies that the client ID set with {@link KafkaAdminClientConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied
     * when it is present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {
        final String userProvidedClientId = "custom-client";

        final KafkaAdminClientConfigProperties config = new KafkaAdminClientConfigProperties();
        config.setAdminClientConfig(Map.of("client.id", userProvidedClientId));
        config.setDefaultClientIdPrefix("other-client");

        final Map<String, String> adminClientConfig = config.getAdminClientConfig("adminClientName");
        assertThat(adminClientConfig.get("client.id")).startsWith(userProvidedClientId + "-adminClientName-");
    }

}
