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


package org.eclipse.hono.service.http;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class HttpServiceConfigOptionsTest {

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link HttpServiceConfigOptions} instance.
     */
    @Test
    public void testHttpServiceConfigOptionsBinding() {

        final var props = new HttpServiceConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        HttpServiceConfigOptions.class,
                        this.getClass().getResource("/http-service-config-options.yaml")));

        assertThat(props.isAuthenticationRequired()).isFalse();
        assertThat(props.getRealm()).isEqualTo("test-realm");
        assertThat(props.getMaxPayloadSize()).isEqualTo(4096);
        assertThat(props.getIdleTimeout()).isEqualTo(30);
    }
}
