/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the behavior of {@link ProtocolAdapterProperties}.
 *
 */
class ProtocolAdapterPropertiesTest {

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ProtocolAdapterOptions} instance.
     */
    @Test
    public void testProtocolAdapterOptionsBinding() {

        final ProtocolAdapterProperties props = new ProtocolAdapterProperties(
                ConfigMappingSupport.getConfigMapping(
                        ProtocolAdapterOptions.class,
                        this.getClass().getResource("/protocol-adapter-options.yaml")));

        final MapperEndpoint telemetryMapper = props.getMapperEndpoint("telemetry");
        assertThat(telemetryMapper).isNotNull();
        assertThat(telemetryMapper.getUri()).isEqualTo("https://mapper.eclipseprojects.io/telemetry");
        assertThat(telemetryMapper.isTlsEnabled()).isTrue();
    }
}
