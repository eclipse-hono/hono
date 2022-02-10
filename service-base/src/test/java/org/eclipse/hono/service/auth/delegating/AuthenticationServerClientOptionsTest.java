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


package org.eclipse.hono.service.auth.delegating;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class AuthenticationServerClientOptionsTest {

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link AuthenticationServerClientOptions} instance.
     */
    @Test
    public void testAuthenticationServerClientOptionsBinding() {

        final var props = new AuthenticationServerClientConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        AuthenticationServerClientOptions.class,
                        this.getClass().getResource("/auth-server-client-options.yaml")));

        assertThat(props.getServerRole()).isEqualTo("Authentication Server");
        assertThat(props.getSupportedSaslMechanisms()).containsExactly("PLAIN");
        assertThat(props.getValidation().getCertPath()).isEqualTo("/etc/cert.pem");
        assertThat(props.getValidation().getKeyPath()).isEqualTo("/etc/key.pem");
        assertThat(props.getValidation().getSharedSecret()).isEqualTo("secret");
        assertThat(props.getValidation().getTokenExpiration()).isEqualTo(300);
    }
}
