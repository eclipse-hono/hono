/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.amqp.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.hono.config.FileFormat;
import org.eclipse.hono.config.GenericOptions;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link AuthenticatingClientConfigProperties}.
 *
 */
public class AuthenticatingClientConfigPropertiesTest {

    private final Path resources = Paths.get("src/test/resources");

    /**
     * Verifies that username and password are read from a credentials
     * property file.
     */
    @Test
    public void testLoadCredentialsFromPropertiesFile() {

        final String path = resources.resolve("credentials").toString();
        final AuthenticatingClientConfigProperties props = new AuthenticatingClientConfigProperties();
        props.setCredentialsPath(path);
        assertThat(props.getUsername()).isEqualTo("foo");
        assertThat(props.getPassword()).isEqualTo("bar");
    }

    /**
     * Verifies that the constructor accepting another instance
     * copies all properties.
     */
    @Test
    public void testCreateFromOtherProperties() {

        final AuthenticatingClientConfigProperties other = new AuthenticatingClientConfigProperties();
        other.setCredentialsPath("credentials/path");
        other.setHost("host");
        other.setHostnameVerificationRequired(false);
        other.setPassword("password");
        other.setPort(12000);
        other.setServerRole("role");
        other.setTlsEnabled(true);
        other.setUsername("user");

        // AbstractConfig properties
        other.setTrustStorePath("path/to/truststore");
        other.setKeyFormat(FileFormat.PEM);

        final AuthenticatingClientConfigProperties newProps = new AuthenticatingClientConfigProperties(other);
        assertThat(newProps.getCredentialsPath()).isEqualTo("credentials/path");
        assertThat(newProps.getHost()).isEqualTo("host");
        assertThat(newProps.isHostnameVerificationRequired()).isFalse();
        assertThat(newProps.getPassword()).isEqualTo("password");
        assertThat(newProps.getPort()).isEqualTo(12000);
        assertThat(newProps.getServerRole()).isEqualTo("role");
        assertThat(newProps.isTlsEnabled()).isTrue();
        assertThat(newProps.getUsername()).isEqualTo("user");
        // AbstractConfig props
        assertThat(newProps.getTrustStorePath()).isEqualTo("path/to/truststore");
        assertThat(newProps.getKeyFormat()).isEqualTo(FileFormat.PEM);
    }

    /**
     * Verifies that the constructor accepting options does not set the host name
     * to the default value.
     */
    @Test
    public void testCreateFromOptions() {
        final GenericOptions genericOptions = mock(GenericOptions.class);
        final AuthenticatingClientOptions options = mock(AuthenticatingClientOptions.class);
        when(options.genericOptions()).thenReturn(genericOptions);
        when(options.host()).thenReturn(Optional.empty());
        when(options.username()).thenReturn(Optional.empty());
        when(options.password()).thenReturn(Optional.empty());
        when(options.serverRole()).thenReturn(Constants.SERVER_ROLE_UNKNOWN);

        final AuthenticatingClientConfigProperties props = new AuthenticatingClientConfigProperties(options);
        assertThat(props.isHostConfigured()).isFalse();
    }
}
