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


package org.eclipse.hono.config.quarkus;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientOptions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigMapping;

/**
 * Tests verifying the binding of configuration properties to objects.
 *
 */
@QuarkusTest
public class QuarkusPropertyBindingTest {

    @Inject
    ApplicationOptions applicationOptions;

    @Inject
    @ConfigMapping(prefix = "hono.command", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
    ClientOptions clientOptions;

    @Inject
    @ConfigMapping(prefix = "hono.tenant", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
    RequestResponseClientOptions tenantClientOptions;

    @Inject
    @ConfigMapping(prefix = "hono.server", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
    ServerOptions serverOptions;

    @Inject
    @ConfigMapping(prefix = "hono.amqp", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
    ServiceOptions serviceOptions;

    @Inject
    @ConfigMapping(prefix = "hono.auth", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
    AuthenticationServerClientOptions authServerClientOptions;

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to an
     * {@link ApplicationOptions} instance.
     */
    @Test
    public void testApplicationOptionsBinding() {
        assertThat(applicationOptions).isNotNull();
        final var props = new ApplicationConfigProperties(applicationOptions);
        assertThat(props.getMaxInstances()).isEqualTo(1);
        assertThat(props.getStartupTimeout()).isEqualTo(60);
    }

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ClientOptions} instance.
     */
    @Test
    public void testClientOptionsBinding() {
        assertThat(clientOptions).isNotNull();
        final var props = new ClientConfigProperties(clientOptions);
        assertThat(props.getAddressRewritePattern().pattern()).isEqualTo("([a-z_]+)/([\\w-]+)");
        assertThat(props.getAddressRewriteReplacement()).isEqualTo("test-vhost/$1/$2");
        assertThat(props.getAmqpHostname()).isEqualTo("command.hono.eclipseprojects.io");
        assertThat(props.getCertPath()).isEqualTo("/etc/cert.pem");
        assertThat(props.getConnectTimeout()).isEqualTo(1234);
        assertThat(props.getCredentialsPath()).isEqualTo("/etc/creds");
        assertThat(props.getFlowLatency()).isEqualTo(321);
        assertThat(props.getHeartbeatInterval()).isEqualTo(22222);
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
     * {@link RequestResponseClientOptions} instance.
     */
    @Test
    public void testRequestResponseClientOptionsBinding() {
        assertThat(tenantClientOptions).isNotNull();
        final var props = new RequestResponseClientConfigProperties(tenantClientOptions);
        assertThat(props.getServerRole()).isEqualTo("Tenant");
        assertThat(props.getResponseCacheDefaultTimeout()).isEqualTo(300);
        assertThat(props.getResponseCacheMaxSize()).isEqualTo(121212);
        assertThat(props.getResponseCacheMinSize()).isEqualTo(333);
    }

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link AuthenticationServerClientOptions} instance.
     */
    @Test
    public void testAuthenticationServerClientOptionsBinding() {
        assertThat(authServerClientOptions).isNotNull();
        final var props = new AuthenticationServerClientConfigProperties(authServerClientOptions);
        assertThat(props.getServerRole()).isEqualTo("Authentication Server");
        assertThat(props.getSupportedSaslMechanisms()).containsExactly("PLAIN");
        assertThat(props.getValidation().getCertPath()).isEqualTo("/etc/cert.pem");
        assertThat(props.getValidation().getKeyPath()).isEqualTo("/etc/key.pem");
        assertThat(props.getValidation().getSharedSecret()).isEqualTo("secret");
        assertThat(props.getValidation().getTokenExpiration()).isEqualTo(300);
    }

    /**
     * Verifies that Quarkus correctly binds properties from a yaml file to a
     * {@link ServerOptions} instance.
     */
    @Test
    public void testServerOptionsBinding() {
        assertThat(serverOptions).isNotNull();
        final var props = new ServerConfig(serverOptions);
        assertThat(props.getBindAddress()).isEqualTo("10.2.0.1");
        assertThat(props.getInsecurePort()).isEqualTo(11001);
        assertThat(props.getInsecurePortBindAddress()).isEqualTo("10.2.0.2");
        assertThat(props.getPort()).isEqualTo(11000);
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
        assertThat(serviceOptions).isNotNull();
        final var props = new ServiceConfigProperties(serviceOptions);
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

}
