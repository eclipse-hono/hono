/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.config;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class ConfigMappingTest {

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ServerOptions} instance.
     */
    @Test
    public void testServerOptionsBinding() {

        final ServerConfig props = new ServerConfig(
                ConfigMappingSupport.getConfigMapping(
                        ServerOptions.class,
                        this.getClass().getResource("/server-options.yaml")));

        assertThat(props.getBindAddress()).isEqualTo("10.2.0.1");
        assertThat(props.getInsecurePort()).isEqualTo(11001);
        assertThat(props.getInsecurePortBindAddress()).isEqualTo("10.2.0.2");
        assertThat(props.getKeyFormat()).isEqualTo(FileFormat.PEM);
        assertThat(props.getPort()).isEqualTo(11000);
        assertThat(props.getTrustStoreFormat()).isEqualTo(FileFormat.JKS);
        assertThat(props.isInsecurePortEnabled()).isTrue();
        assertThat(props.isNativeTlsRequired()).isTrue();
        assertThat(props.isSecurePortEnabled()).isFalse();
        assertThat(props.isSni()).isFalse();
    }

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ServiceOptions} instance.
     */
    @Test
    public void testServiceOptionsBinding() {

        final ServiceConfigProperties props = new ServiceConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        ServiceOptions.class,
                        this.getClass().getResource("/service-options.yaml")));

        assertThat(props.getCorsAllowedOrigin()).isEqualTo("client.eclipse.org");
        assertThat(props.getDeviceIdPattern().pattern()).isEqualTo("[a-z]+");
        assertThat(props.getEventLoopBlockedCheckTimeout()).isEqualTo(12000);
        assertThat(props.getMaxPayloadSize()).isEqualTo(128000);
        assertThat(props.getReceiverLinkCredit()).isEqualTo(1000);
        assertThat(props.getSendTimeOut()).isEqualTo(5100);
        assertThat(props.getTenantIdPattern().pattern()).isEqualTo("[A-Z]+");
        assertThat(props.isNetworkDebugLoggingEnabled()).isTrue();
        assertThat(props.isWaitForDownstreamConnectionEnabled()).isTrue();
    }
}
