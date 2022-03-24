/**
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

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Verifies the creation of {@link KafkaAdminClientConfigProperties} from {@link KafkaAdminClientOptions}.
 *
 */
class KafkaAdminClientConfigPropertiesQuarkusTest {

    @Test
    void testAdminClientOptionsAreSet() {

        final var commonOptions = ConfigMappingSupport.getConfigMapping(
                CommonKafkaClientOptions.class,
                this.getClass().getResource("/admin-options.yaml"),
                "hono.kafka");
        final var adminClientOptions = ConfigMappingSupport.getConfigMapping(
                KafkaAdminClientOptions.class,
                this.getClass().getResource("/admin-options.yaml"),
                "hono.kafka.adminClientTest");
        final var config = new KafkaAdminClientConfigProperties(commonOptions, adminClientOptions);

        assertAll(() -> assertThat(config.getAdminClientConfig("test").get("common.property")).isEqualTo("present"),
                () -> assertThat(config.getAdminClientConfig("test").get("admin.property")).isEqualTo("admin"),
                () -> assertThat(config.getAdminClientConfig("test").get("number")).isEqualTo("123"),
                () -> assertThat(config.getAdminClientConfig("test").get("empty")).isEqualTo(""));
    }
}
