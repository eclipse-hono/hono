/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.jdbc.config;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.time.Duration;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.smallrye.config.ConfigValidationException;

/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class QuarkusConfigMappingTest {

    @Test
    void testValidTtlIsMappedCorrectly() throws IOException {

        Assertions.assertAll(
            () -> {
                final var deviceServiceOptions = ConfigMappingSupport.getConfigMapping(
                            DeviceServiceOptions.class,
                            this.getClass().getResource("/ttl-valid-config.yaml"));
                assertThat(deviceServiceOptions.credentialsTtl()).isEqualTo(Duration.ofSeconds(0));
                assertThat(deviceServiceOptions.registrationTtl()).isEqualTo(Duration.ofSeconds(140));
            },
            () -> {
                final var tenantServiceOptions = ConfigMappingSupport.getConfigMapping(
                            TenantServiceOptions.class,
                            this.getClass().getResource("/ttl-valid-config.yaml"));
                assertThat(tenantServiceOptions.tenantTtl()).isEqualTo(Duration.ofMinutes(10));
            }
        );
    }

    @Test
    void testMappingOfInvalidTtlFails() throws IOException {

        Assertions.assertAll(
            () -> {
                final var exception = Assert.assertThrows(AssertionError.class, () ->
                ConfigMappingSupport.getConfigMapping(
                        DeviceServiceOptions.class,
                        this.getClass().getResource("/ttl-invalid-config.yaml")));
                assertThat(exception).hasCauseThat().isInstanceOf(ConfigValidationException.class);
            },
            () -> {
                final var exception = Assert.assertThrows(AssertionError.class, () ->
                ConfigMappingSupport.getConfigMapping(
                        TenantServiceOptions.class,
                        this.getClass().getResource("/ttl-invalid-config.yaml")));
                assertThat(exception).hasCauseThat().isInstanceOf(ConfigValidationException.class);
            }
        );
    }
}
