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

package org.eclipse.hono.adapter.quarkus;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.config.ProtocolAdapterOptions;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that Quarkus binds properties from yaml files to configuration objects.
 */
@QuarkusTest
public class QuarkusPropertyBindingTest {

    @Inject
    ProtocolAdapterOptions protocolAdapterOptions;

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ProtocolAdapterOptions} instance.
     */
    @Test
    public void testProtocolAdapterOptionsBinding() {
        assertThat(protocolAdapterOptions).isNotNull();
        final var props = new ProtocolAdapterProperties(protocolAdapterOptions);
        final MapperEndpoint telemetryMapper = props.getMapperEndpoint("telemetry");
        assertThat(telemetryMapper).isNotNull();
        assertThat(telemetryMapper.getUri()).isEqualTo("https://mapper.eclipseprojects.io/telemetry");
        assertThat(telemetryMapper.isTlsEnabled()).isTrue();
    }
}
