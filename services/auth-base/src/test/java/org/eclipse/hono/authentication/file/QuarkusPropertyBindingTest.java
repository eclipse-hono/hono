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


package org.eclipse.hono.authentication.file;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.eclipse.hono.util.AuthenticationConstants;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests verifying the binding of configuration properties to objects.
 *
 */
@QuarkusTest
public class QuarkusPropertyBindingTest {

    @Inject
    FileBasedAuthenticationServiceOptions serviceOptions;

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link FileBasedAuthenticationServiceOptions} instance.
     */
    @Test
    public void testServiceOptionsBinding() {
        assertThat(serviceOptions).isNotNull();
        final var props = new FileBasedAuthenticationServiceConfigProperties(serviceOptions);
        assertThat(props.getPermissionsPath()).isEqualTo("/etc/permissions.json");
        assertThat(props.getSigning().getSharedSecret()).isEqualTo("secret");
        assertThat(props.getSigning().getTokenExpiration()).isEqualTo(1000);
        assertThat(props.getSupportedSaslMechanisms()).containsExactly(AuthenticationConstants.MECHANISM_PLAIN);
        assertThat(props.getValidation().getSharedSecret()).isEqualTo("secret");
        assertThat(props.getValidation().getTokenExpiration()).isEqualTo(1000);
    }
}
