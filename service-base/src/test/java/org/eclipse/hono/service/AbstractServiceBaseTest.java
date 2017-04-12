/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.eclipse.hono.config.HonoConfigProperties;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Hono Server Base class.
 *
 */
public class AbstractServiceBaseTest {

    private static final int PORT_NR          = 4711;
    private static final int INSECURE_PORT_NR = 4712;

    private Vertx vertx;
    private EventBus eventBus;

    /**
     * Sets up common mock objects used by the test cases.
     */
    @Before
    public void initMocks() {
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
    }

    private AbstractServiceBase createServer() {
        AbstractServiceBase server = new AbstractServiceBase() {
            @Override
            public int getPortDefaultValue() {
                return PORT_NR;
            }

            @Override
            public int getInsecurePortDefaultValue() {
                return INSECURE_PORT_NR;
            }

            @Override
            public int getPort() {
                return port;
            }

            @Override
            public int getInsecurePort() {
                return insecurePort;
            }
        };
        return server;
    }

    /**
     * Verifies that a Hono server will bind to the default port only
     * when using a default configuration with only the key store property being set.
     */
    @Test
    public void checkSecurePortAutoSelect() {

        // GIVEN a configuration with a key store set
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");

        // WHEN using this configuration to determine the server's port configuration
        // secure port config: no port set -> secure IANA port selected
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

        // THEN the default secure port is selected and no insecure port will be opened
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isOpenSecurePort());
        assertThat(server.getPort(), is(PORT_NR));
        assertFalse(server.isOpenInsecurePort());
    }

    /**
     * Verifies that a Hono server will bind to the configured secure port only
     * when using a default configuration with only the key store and the port property being set.
     */
    @Test
    public void checkSecurePortExplicitlySet() {

        // GIVEN a configuration with a key store and a secure port being set
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");
        configProperties.setPort(8989);

        // WHEN using this configuration to determine the server's port configuration
        // secure port config: explicit port set -> port used
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

        // THEN the configured port is used and no insecure port will be opened
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isOpenSecurePort());
        assertThat(server.getPort(), is(8989));
        assertFalse(server.isOpenInsecurePort());
    }

    /**
     * Verifies that a Hono server will not be able to start
     * when using a default configuration with not key store being set.
     */
    @Test
    public void checkNoPortsSet() {

        // GIVEN a default configuration with no key store being set
        HonoConfigProperties configProperties = new HonoConfigProperties();

        // WHEN using this configuration to determine the server's port configuration
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

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
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);

        // WHEN using this configuration to determine the server's port configuration
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

        // THEN the server will bind to the default insecure port only
        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isOpenSecurePort());
        assertTrue(server.isOpenInsecurePort());
        assertThat(server.getInsecurePort(), is(INSECURE_PORT_NR));
    }

    /**
     * Verifies that a Hono server will bind to a configured insecure port only
     * when using a default configuration with the insecure port property being set.
     */
    @Test
    public void checkInsecureOnlyPortExplicitlySet() {

        // GIVEN a default configuration with insecure port being set to a specific port.
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(8888);

        // WHEN using this configuration to determine the server's port configuration
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

        // THEN the server will bind to the configured insecure port only
        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isOpenSecurePort());
        assertTrue(server.isOpenInsecurePort());
        assertThat(server.getInsecurePort(), is(8888));
    }

    /**
     * Verifies that a Hono server will bind to both the default insecure and secure ports
     * when using a default configuration with the insecure port being enabled and the
     * key store property being set.
     */
    @Test
    public void checkBothPortsOpen() {

        // GIVEN a default configuration with insecure port being enabled and a key store being set.
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");

        // WHEN using this configuration to determine the server's port configuration
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

        // THEN the server will bind to both the default insecure and secure ports
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isOpenSecurePort());
        assertThat(server.getPort(), is(PORT_NR));
        assertTrue(server.isOpenInsecurePort());
        assertThat(server.getInsecurePort(), is(INSECURE_PORT_NR));
    }

    /**
     * Verifies that a Hono server will not start
     * when using a default configuration with both secure and insecure ports being enabled and
     * set to the same port number.
     */
    @Test
    public void checkBothPortsSetToSame() {

        // GIVEN a default configuration with both the insecure port and the secure port
        // being set to the same value.
        HonoConfigProperties configProperties = new HonoConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath("/etc/hono/certs/honoKeyStore.p12");
        configProperties.setInsecurePort(8888);
        configProperties.setPort(8888);

        // WHEN using this configuration to determine the server's port configuration
        Future<Void> portConfigurationTracker = Future.future();
        AbstractServiceBase server = createServer();
        server.setConfig(configProperties);
        server.determinePortConfigurations(portConfigurationTracker);

        // THEN port configuration fails
        assertTrue(portConfigurationTracker.failed());
    }




}
