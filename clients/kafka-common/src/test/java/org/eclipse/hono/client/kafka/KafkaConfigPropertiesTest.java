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


package org.eclipse.hono.client.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link KafkaConfigProperties}.
 */
class KafkaConfigPropertiesTest {

    static final String PROPERTY_FILE_CLIENT_ID = "target/test-classes/clientid.properties";
    static final String PROPERTY_FILE_COMMON = "target/test-classes/common.properties";
    static final String PROPERTY_FILE_ADMIN = "target/test-classes/admin.properties";

    /**
     * Verifies that the property file list cannot be {@code null}.
     */
    @Test
    public void testThatConfigFilesCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaConfigProperties().setPropertyFiles(null));
    }

    /**
     * Verifies that the client ID can not be set to {@code null}.
     */
    @Test
    public void testThatClientIdCanNotBeSetToNull() {
        assertThrows(NullPointerException.class, () -> new KafkaConfigProperties().setDefaultClientIdPrefix(null));
    }

    /**
     * Verifies that properties are successfully read from configured files.
     */
    @Test
    public void testGetConfigReturnsPropertiesFromFiles() {
        final var config = new KafkaConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_COMMON, PROPERTY_FILE_ADMIN));

        final Map<String, String> adminClientConfig = config.getConfig();
        assertThat(adminClientConfig.get("foo")).isEqualTo("bar");
        assertThat(adminClientConfig.get("common")).isEqualTo("commonValue");
    }

    /**
     * Verifies that non-existing property file is detected.
     */
    @Test
    public void testSetPropertyFilesDetectsMissingFile() {
        final var config = new KafkaConfigProperties();
        assertThrows(
                IllegalArgumentException.class,
                () -> config.setPropertyFiles(List.of("non-existing.properties")));
    }

    /**
     * Verifies that properties are considered <em>configured</em> only if <em>bootstrap.servers</em>
     * have been set.
     */
    @Test
    public void testIsConfiguredMethod() {

        assertThat(new KafkaConfigProperties().isConfigured()).isFalse();

        final var config = new KafkaConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_COMMON));
        assertThat(config.isConfigured()).isTrue();
    }
}
