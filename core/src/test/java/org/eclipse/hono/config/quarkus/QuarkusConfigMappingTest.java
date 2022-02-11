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


package org.eclipse.hono.config.quarkus;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.FileFormat;
import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.config.ProtocolAdapterOptions;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the mapping of YAML properties to configuration classes.
 *
 */
class QuarkusConfigMappingTest {

    @Test
    void testApplicationOptionsBinding() {

        final ApplicationConfigProperties props = new ApplicationConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        ApplicationOptions.class,
                        this.getClass().getResource("/application-options.yaml")));

        assertThat(props.getMaxInstances()).isEqualTo(1);
    }

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ClientOptions} instance.
     */
    @Test
    public void testClientOptionsBinding() {

        final ClientConfigProperties props = new ClientConfigProperties(
                ConfigMappingSupport.getConfigMapping(
                        ClientOptions.class,
                        this.getClass().getResource("/client-options.yaml")));

        assertThat(props.getAddressRewritePattern().pattern()).isEqualTo("([a-z_]+)/([\\w-]+)");
        assertThat(props.getAddressRewriteReplacement()).isEqualTo("test-vhost/$1/$2");
        assertThat(props.getAmqpHostname()).isEqualTo("command.hono.eclipseprojects.io");
        assertThat(props.getCertPath()).isEqualTo("/etc/cert.pem");
        assertThat(props.getConnectTimeout()).isEqualTo(1234);
        assertThat(props.getCredentialsPath()).isEqualTo("/etc/creds");
        assertThat(props.getFlowLatency()).isEqualTo(321);
        assertThat(props.getHeartbeatInterval()).isEqualTo(22222);
        assertThat(props.isHostConfigured()).isTrue();
        assertThat(props.getHost()).isEqualTo("hono.eclipseprojects.io");
        assertThat(props.getIdleTimeout()).isEqualTo(44444);
        assertThat(props.getInitialCredits()).isEqualTo(55);
        assertThat(props.getKeyFormat()).isNull();
        assertThat(props.getKeyPath()).isEqualTo("/etc/key.pem");
        assertThat(props.getLinkEstablishmentTimeout()).isEqualTo(1111);
        assertThat(props.getMaxFrameSize()).isEqualTo(32000);
        assertThat(props.getMaxMessageSize()).isEqualTo(64000);
        assertThat(props.getMaxSessionFrames()).isEqualTo(30);
        assertThat(props.getMaxSessionWindowSize()).isEqualTo(30 * 32000);
        assertThat(props.getMinMaxMessageSize()).isEqualTo(65000);
        assertThat(props.getName()).isEqualTo("client");
        assertThat(props.getPassword()).isEqualTo("secret");
        assertThat(props.getPathSeparator()).isEqualTo("-");
        assertThat(props.getPort()).isEqualTo(12000);
        assertThat(props.getReconnectAttempts()).isEqualTo(12);
        assertThat(props.getReconnectDelayIncrement()).isEqualTo(100); // default
        assertThat(props.getReconnectMaxDelay()).isEqualTo(412);
        assertThat(props.getReconnectMinDelay()).isEqualTo(10);
        assertThat(props.getRequestTimeout()).isEqualTo(533);
        assertThat(props.getSecureProtocols()).containsExactly("TLSv1.0", "TLSv1.5");
        assertThat(props.getSendMessageTimeout()).isEqualTo(2121);
        assertThat(props.getServerRole()).isEqualTo("bumlux");
        assertThat(props.getSupportedCipherSuites()).isEmpty(); // default
        assertThat(props.isTlsEnabled()).isTrue();
        assertThat(props.getTrustStorePath()).isEqualTo("/etc/trusted-certs.pem");
        assertThat(props.getUsername()).isEqualTo("user");
    }

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
