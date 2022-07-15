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


package org.eclipse.hono.authentication.file;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.test.ConfigMappingSupport;
import org.eclipse.hono.util.AuthenticationConstants;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the binding of configuration properties to objects.
 *
 */
public class FileBasedAuthenticationServiceOptionsTest {

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link FileBasedAuthenticationServiceOptions} instance.
     */
    @Test
    public void testServiceOptionsBinding() {

        final var props = new FileBasedAuthenticationServiceConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        FileBasedAuthenticationServiceOptions.class,
                        this.getClass().getResource("/auth-service-options.yaml")));

        assertThat(props.getPermissionsPath()).isEqualTo("/etc/permissions.json");
        assertThat(props.getSigning().getAudience()).isEqualTo("hono-components");
        assertThat(props.getSigning().getSharedSecret()).isEqualTo("secretsecretsecretsecretsecretsecret");
        assertThat(props.getSigning().getTokenExpiration()).isEqualTo(1000);
        assertThat(props.getSigning().getIssuer()).isEqualTo("https://my.auth-server.io");
        assertThat(props.getSupportedSaslMechanisms()).containsExactly(AuthenticationConstants.MECHANISM_PLAIN);
        assertThat(props.getValidation().getAudience()).isEqualTo("hono-components");
        assertThat(props.getValidation().getSharedSecret()).isEqualTo("secretsecretsecretsecretsecretsecret");
        assertThat(props.getValidation().getTokenExpiration()).isEqualTo(1000);
        assertThat(props.getValidation().getIssuer()).isEqualTo("https://my.auth-server.io");
    }
}
