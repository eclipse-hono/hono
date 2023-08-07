/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.config;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.config.FileFormat;
import org.junit.jupiter.api.Test;


/**
 * Tests verifying behavior of {@link ClientConfigProperties}.
 *
 */
public class ClientConfigPropertiesTest {

    /**
     * Verifies that the constructor accepting another instance
     * copies all properties.
     */
    @Test
    public void testCreateFromOtherProperties() {

        final ClientConfigProperties other = new ClientConfigProperties();
        other.setAddressRewriteRule("([a-z_]+)/([\\w-]+) test-vhost/$1/$2");
        other.setAmqpHostname("virtual-host");
        other.setConnectTimeout(1000);
        other.setFlowLatency(500);
        other.setIdleTimeout(5000);
        other.setInitialCredits(200);
        other.setLinkEstablishmentTimeout(500);
        other.setMaxFrameSize(64 * 1024);
        other.setMaxMessageSize(3000L);
        other.setMaxSessionFrames(100);
        other.setMinMaxMessageSize(16 * 1024);
        other.setName("name");
        other.setReconnectAttempts(10);
        other.setReconnectDelayIncrement(100);
        other.setReconnectMaxDelay(40000);
        other.setReconnectMinDelay(100);
        other.setRequestTimeout(2000);
        other.setSendMessageTimeout(1000);

        // AbstractConfig properties
        other.setTrustStorePath("path/to/truststore");
        other.setKeyFormat(FileFormat.PEM);

        /// AuthenticatingClientConfigProperties
        other.setUsername("user");

        final ClientConfigProperties newProps = new ClientConfigProperties(other);
        assertThat(newProps.getAddressRewritePattern().pattern()).isEqualTo("([a-z_]+)/([\\w-]+)");
        assertThat(newProps.getAddressRewriteReplacement()).isEqualTo("test-vhost/$1/$2");
        assertThat(newProps.getAmqpHostname()).isEqualTo("virtual-host");
        assertThat(newProps.getConnectTimeout()).isEqualTo(1000);
        assertThat(newProps.getFlowLatency()).isEqualTo(500);
        assertThat(newProps.getIdleTimeout()).isEqualTo(5000);
        assertThat(newProps.getInitialCredits()).isEqualTo(200);
        assertThat(newProps.getLinkEstablishmentTimeout()).isEqualTo(500);
        assertThat(newProps.getMaxFrameSize()).isEqualTo(64 * 1024);
        assertThat(newProps.getMaxMessageSize()).isEqualTo(3000L);
        assertThat(newProps.getMaxSessionFrames()).isEqualTo(100);
        assertThat(newProps.getMinMaxMessageSize()).isEqualTo(16 * 1024);
        assertThat(newProps.getName()).isEqualTo("name");
        assertThat(newProps.getReconnectAttempts()).isEqualTo(10);
        assertThat(newProps.getReconnectDelayIncrement()).isEqualTo(100);
        assertThat(newProps.getReconnectMaxDelay()).isEqualTo(40000);
        assertThat(newProps.getReconnectMinDelay()).isEqualTo(100);
        assertThat(newProps.getRequestTimeout()).isEqualTo(2000);
        assertThat(newProps.getSendMessageTimeout()).isEqualTo(1000);
        // AbstractConfig props
        assertThat(newProps.getTrustStorePath()).isEqualTo("path/to/truststore");
        assertThat(newProps.getKeyFormat()).isEqualTo(FileFormat.PEM);
        // AuthenticatingClientConfigProperties
        assertThat(newProps.getUsername()).isEqualTo("user");
    }
}
