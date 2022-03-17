/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.net.NetServerOptions;

/**
 * Unit tests for Hono Server Base class.
 *
 */
public class AbstractServiceBaseTest {

    private static final int PORT_NR          = 4711;
    private static final int INSECURE_PORT_NR = 4712;

    private static final String PREFIX_KEY_PATH = "target/certs/";

    private Vertx vertx;
    private EventBus eventBus;

    /**
     * Sets up common mock objects used by the test cases.
     */
    @BeforeEach
    public void initMocks() {
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
    }

    private AbstractServiceBase<ServiceConfigProperties> createService(final ServiceConfigProperties config) {

        final AbstractServiceBase<ServiceConfigProperties> server = new AbstractServiceBase<>() {

            @Override
            public int getPortDefaultValue() {
                return PORT_NR;
            }

            @Override
            public int getInsecurePortDefaultValue() {
                return INSECURE_PORT_NR;
            }

            @Override
            protected int getActualPort() {
                return Constants.PORT_UNCONFIGURED;
            }

            @Override
            protected int getActualInsecurePort() {
                return Constants.PORT_UNCONFIGURED;
            }
        };
        server.setConfig(config);
        return server;
    }

    /**
     * Verifies that a Hono server will bind to the default port only
     * when using a default configuration with only the key store property being set.
     */
    @Test
    public void checkSecurePortAutoSelect() {

        // GIVEN a configuration with a key store set
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");

        // WHEN using this configuration to determine the server's port configuration
        // secure port config: no port set -> secure IANA port selected
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the default secure port is selected and no insecure port will be opened
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isSecurePortEnabled());
        assertThat(server.getPort()).isEqualTo(PORT_NR);
        assertFalse(server.isInsecurePortEnabled());
    }

    /**
     * Verifies that a Hono server will bind to the configured secure port only
     * when using a default configuration with only the key store and the port property being set.
     */
    @Test
    public void checkSecurePortExplicitlySet() {

        // GIVEN a configuration with a key store and a secure port being set
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");
        configProperties.setPort(8989);

        // WHEN using this configuration to determine the server's port configuration
        // secure port config: explicit port set -> port used
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the configured port is used and no insecure port will be opened
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isSecurePortEnabled());
        assertThat(server.getPort()).isEqualTo(8989);
        assertFalse(server.isInsecurePortEnabled());
    }

    /**
     * Verifies that a Hono server will not be able to start
     * when using a default configuration with not key store being set.
     */
    @Test
    public void checkNoPortsSet() {

        // GIVEN a default configuration with no key store being set
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();

        // WHEN using this configuration to determine the server's port configuration
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the port configuration fails
        assertTrue(portConfigurationTracker.failed());
    }

    /**
     * Verifies that a Hono server will bind to the default insecure port only
     * when using a default configuration with the insecure port being enabled.
     */
    @Test
    public void checkInsecureOnlyPort() {

        // GIVEN a default configuration with insecure port being enabled but no key store being set
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);

        // WHEN using this configuration to determine the server's port configuration
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the server will bind to the default insecure port only
        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isSecurePortEnabled());
        assertTrue(server.isInsecurePortEnabled());
        assertThat(server.getInsecurePort()).isEqualTo(INSECURE_PORT_NR);
    }

    /**
     * Verifies that a Hono server will bind to a configured insecure port only
     * when using a default configuration with the insecure port property being set.
     */
    @Test
    public void checkInsecureOnlyPortExplicitlySet() {

        // GIVEN a default configuration with insecure port being set to a specific port.
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(8888);

        // WHEN using this configuration to determine the server's port configuration
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the server will bind to the configured insecure port only
        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isSecurePortEnabled());
        assertTrue(server.isInsecurePortEnabled());
        assertThat(server.getInsecurePort()).isEqualTo(8888);
    }

    /**
     * Verifies that a Hono server will bind to both the default insecure and secure ports
     * when using a default configuration with the insecure port being enabled and the
     * key store property being set.
     */
    @Test
    public void checkBothPortsOpen() {

        // GIVEN a default configuration with insecure port being enabled and a key store being set.
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");

        // WHEN using this configuration to determine the server's port configuration
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the server will bind to both the default insecure and secure ports
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isSecurePortEnabled());
        assertThat(server.getPort()).isEqualTo(PORT_NR);
        assertTrue(server.isInsecurePortEnabled());
        assertThat(server.getInsecurePort()).isEqualTo(INSECURE_PORT_NR);
    }

    /**
     * Verifies that a Hono server will only bind to the secure port
     * when using a default configuration with both secure and insecure ports being enabled and
     * set to the same port number.
     */
    @Test
    public void checkBothPortsSetToSame() {

        // GIVEN a default configuration with both the insecure port and the secure port
        // being set to the same value.
        final ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");
        configProperties.setInsecurePort(8888);
        configProperties.setPort(8888);

        // WHEN using this configuration to determine the server's port configuration
        final AbstractServiceBase<ServiceConfigProperties> server = createService(configProperties);
        final Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN port configuration fails
        assertTrue(portConfigurationTracker.failed());
    }

    /**
     * Verifies that only TLSv1.2 and TLSv1.3 are enabled by default.
     *
     */
    @Test
    public void testAddTlsKeyCertOptionsDisablesAllProtocolVersionsButTls12() {

        // GIVEN a default configuration for TLS
        final ServiceConfigProperties config = new ServiceConfigProperties();
        config.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");

        // WHEN configuring a service using the configuration
        final AbstractServiceBase<ServiceConfigProperties> service = createService(config);
        final NetServerOptions options = new NetServerOptions();
        service.addTlsKeyCertOptions(options);

        // THEN TLS 1.3 and 1.2 are enabled
        assertTrue(options.isSsl());
        assertThat(options.getEnabledSecureTransportProtocols()).containsExactly("TLSv1.3", "TLSv1.2");
    }

    /**
     * Verifies that only the configured TLS protocols are enabled.
     *
     */
    @Test
    public void testAddTlsKeyCertOptionsDisablesTlsProtocolVersions() {

        // GIVEN a configuration with only TLS 1 and TLS 1.1 enabled
        final ServiceConfigProperties config = new ServiceConfigProperties();
        config.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");
        config.setSecureProtocols(List.of("TLSv1", "TLSv1.1"));

        // WHEN configuring a service using the configuration
        final AbstractServiceBase<ServiceConfigProperties> service = createService(config);
        final NetServerOptions options = new NetServerOptions();
        service.addTlsKeyCertOptions(options);

        // THEN SSL is enabled and only TLSv1 and TLSv1.1 are supported
        assertTrue(options.isSsl());
        assertThat(options.getEnabledSecureTransportProtocols()).containsExactly("TLSv1", "TLSv1.1");
    }

    /**
     * Verifies that all cipher suites are enabled by default.
     *
     */
    @Test
    public void testAddTlsKeyCertOptionsEnablesAllCipherSuitesByDefault() {

        // GIVEN a default configuration for TLS
        final ServiceConfigProperties config = new ServiceConfigProperties();
        config.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");

        // WHEN configuring a service using the configuration
        final AbstractServiceBase<ServiceConfigProperties> service = createService(config);
        final NetServerOptions options = new NetServerOptions();
        service.addTlsKeyCertOptions(options);

        // THEN no specific cipher suites are whitelisted
        assertTrue(options.isSsl());
        assertThat(options.getEnabledCipherSuites()).isEmpty();
    }

    /**
     * Verifies that only the configured TLS cipher suites are enabled.
     *
     */
    @Test
    public void testAddTlsKeyCertOptionsEnablesConfiguredCipherSuites() {

        // GIVEN a configuration with only TLS 1 and TLS 1.1 enabled
        final ServiceConfigProperties config = new ServiceConfigProperties();
        config.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");
        config.setSupportedCipherSuites(List.of("TLS_PSK_WITH_AES_256_CCM_8", "TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8"));

        // WHEN configuring a service using the configuration
        final AbstractServiceBase<ServiceConfigProperties> service = createService(config);
        final NetServerOptions options = new NetServerOptions();
        service.addTlsKeyCertOptions(options);

        // THEN TLS is enabled and only the configured suites are enabled in the correct order
        assertTrue(options.isSsl());
        assertThat(options.getEnabledCipherSuites())
            .containsExactly("TLS_PSK_WITH_AES_256_CCM_8", "TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8");
    }

    /**
     * Verifies that SNI TLS is enabled.
     *
     */
    @Test
    public void testSNIEnable() {

        // GIVEN a default TLS configuration
        final ServiceConfigProperties config = new ServiceConfigProperties();
        config.setKeyStorePath(PREFIX_KEY_PATH + "/authServerKeyStore.p12");
        config.setSecureProtocols(Arrays.asList("TLSv1.1"));
        config.setSni(true);

        // WHEN configuring a service using the configuration
        final AbstractServiceBase<ServiceConfigProperties> service = createService(config);
        final NetServerOptions options = new NetServerOptions();
        service.addTlsKeyCertOptions(options);

        // THEN SSL is enabled and also SNI is enabled
        assertTrue(options.isSsl());
        assertTrue(options.isSni());
    }
}
