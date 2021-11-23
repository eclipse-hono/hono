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

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigMapping;

/**
 * Verifies the creation of {@link KafkaAdminClientConfigProperties} from {@link KafkaAdminClientOptions}.
 *
 */
@QuarkusTest
public class KafkaAdminClientConfigPropertiesQuarkusTest {

    @Inject
    @ConfigMapping(prefix = "hono.kafka")
    CommonKafkaClientOptions commonOptions;

    @Inject
    @ConfigMapping(prefix = "hono.kafka.adminClientTest")
    KafkaAdminClientOptions adminClientOptions;

    private KafkaAdminClientConfigProperties config;

    @BeforeEach
    void setUp() {
        config = new KafkaAdminClientConfigProperties(commonOptions, adminClientOptions);
    }

    /**
     * Asserts that common client properties are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        assertThat(config.getAdminClientConfig("test").get("common.property")).isEqualTo("present");
    }

    /**
     * Asserts that admin client properties are present.
     */
    @Test
    public void testThatAdminClientConfigIsPresent() {
        assertThat(config.getAdminClientConfig("test").get("admin.property")).isEqualTo("admin");
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumbersArePresentAsStrings() {
        assertThat(config.getAdminClientConfig("test").get("number")).isEqualTo("123");
    }

    /**
     * Asserts that properties with an empty string as the value are added.
     */
    @Test
    public void testThatEmptyValuesAreMaintained() {
        assertThat(config.getAdminClientConfig("test").get("empty")).isEqualTo("");
    }

}
