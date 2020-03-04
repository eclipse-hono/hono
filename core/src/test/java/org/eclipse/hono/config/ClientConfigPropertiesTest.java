/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link ClientConfigProperties}.
 *
 */
public class ClientConfigPropertiesTest {

    private final Path resources = Paths.get("src/test/resources");

    /**
     * Verifies that username and password are read from a credentials
     * property file.
     */
    @Test
    public void testLoadCredentialsFromPropertiesFile() {

        final String path = resources.resolve("credentials").toString();
        final ClientConfigProperties props = new ClientConfigProperties();
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

        final ClientConfigProperties other = new ClientConfigProperties();
        other.setAmqpHostname("virtual-host");
        other.setConnectTimeout(1000);
        other.setCredentialsPath("credentials/path");
        other.setFlowLatency(500);
        other.setHost("host");
        other.setHostnameVerificationRequired(false);
        other.setIdleTimeout(5000);
        other.setInitialCredits(200);
        other.setLinkEstablishmentTimeout(500);
        other.setName("name");
        other.setPassword("password");
        other.setPort(12000);
        other.setReconnectAttempts(10);
        other.setReconnectDelayIncrement(100);
        other.setReconnectMaxDelay(40000);
        other.setReconnectMinDelay(100);
        other.setRequestTimeout(2000);
        other.setSendMessageTimeout(1000);
        other.setTlsEnabled(true);
        other.setUsername("user");

        // AbstractConfig properties
        other.setTrustStorePath("path/to/truststore");
        other.setKeyFormat(FileFormat.PEM);

        final ClientConfigProperties newProps = new ClientConfigProperties(other);
        assertThat(newProps.getAmqpHostname()).isEqualTo("virtual-host");
        assertThat(newProps.getConnectTimeout()).isEqualTo(1000);
        assertThat(newProps.getCredentialsPath()).isEqualTo("credentials/path");
        assertThat(newProps.getFlowLatency()).isEqualTo(500);
        assertThat(newProps.getHost()).isEqualTo("host");
        assertThat(newProps.isHostnameVerificationRequired()).isFalse();
        assertThat(newProps.getIdleTimeout()).isEqualTo(5000);
        assertThat(newProps.getInitialCredits()).isEqualTo(200);
        assertThat(newProps.getLinkEstablishmentTimeout()).isEqualTo(500);
        assertThat(newProps.getName()).isEqualTo("name");
        assertThat(newProps.getPassword()).isEqualTo("password");
        assertThat(newProps.getPort()).isEqualTo(12000);
        assertThat(newProps.getReconnectAttempts()).isEqualTo(10);
        assertThat(newProps.getReconnectDelayIncrement()).isEqualTo(100);
        assertThat(newProps.getReconnectMaxDelay()).isEqualTo(40000);
        assertThat(newProps.getReconnectMinDelay()).isEqualTo(100);
        assertThat(newProps.getRequestTimeout()).isEqualTo(2000);
        assertThat(newProps.getSendMessageTimeout()).isEqualTo(1000);
        assertThat(newProps.isTlsEnabled()).isTrue();
        assertThat(newProps.getUsername()).isEqualTo("user");
        // AbstractConfig props
        assertThat(newProps.getTrustStorePath()).isEqualTo("path/to/truststore");
        assertThat(newProps.getKeyFormat()).isEqualTo(FileFormat.PEM);
    }
}
