/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.service;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
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

    private static final String PREFIX_KEY_PATH = "target/certs/";

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

    private AbstractServiceBase<ServiceConfigProperties> createServer(final ServiceConfigProperties config) {

        AbstractServiceBase<ServiceConfigProperties> server = new AbstractServiceBase<ServiceConfigProperties>() {

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

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
        ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/honoKeyStore.p12");

        // WHEN using this configuration to determine the server's port configuration
        // secure port config: no port set -> secure IANA port selected
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the default secure port is selected and no insecure port will be opened
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isSecurePortEnabled());
        assertThat(server.getPort(), is(PORT_NR));
        assertFalse(server.isInsecurePortEnabled());
    }

    /**
     * Verifies that a Hono server will bind to the configured secure port only
     * when using a default configuration with only the key store and the port property being set.
     */
    @Test
    public void checkSecurePortExplicitlySet() {

        // GIVEN a configuration with a key store and a secure port being set
        ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/honoKeyStore.p12");
        configProperties.setPort(8989);

        // WHEN using this configuration to determine the server's port configuration
        // secure port config: explicit port set -> port used
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the configured port is used and no insecure port will be opened
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isSecurePortEnabled());
        assertThat(server.getPort(), is(8989));
        assertFalse(server.isInsecurePortEnabled());
    }

    /**
     * Verifies that a Hono server will not be able to start
     * when using a default configuration with not key store being set.
     */
    @Test
    public void checkNoPortsSet() {

        // GIVEN a default configuration with no key store being set
        ServiceConfigProperties configProperties = new ServiceConfigProperties();

        // WHEN using this configuration to determine the server's port configuration
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

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
        ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);

        // WHEN using this configuration to determine the server's port configuration
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the server will bind to the default insecure port only
        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isSecurePortEnabled());
        assertTrue(server.isInsecurePortEnabled());
        assertThat(server.getInsecurePort(), is(INSECURE_PORT_NR));
    }

    /**
     * Verifies that a Hono server will bind to a configured insecure port only
     * when using a default configuration with the insecure port property being set.
     */
    @Test
    public void checkInsecureOnlyPortExplicitlySet() {

        // GIVEN a default configuration with insecure port being set to a specific port.
        ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(8888);

        // WHEN using this configuration to determine the server's port configuration
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the server will bind to the configured insecure port only
        assertTrue(portConfigurationTracker.succeeded());
        assertFalse(server.isSecurePortEnabled());
        assertTrue(server.isInsecurePortEnabled());
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
        ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/honoKeyStore.p12");

        // WHEN using this configuration to determine the server's port configuration
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN the server will bind to both the default insecure and secure ports
        assertTrue(portConfigurationTracker.succeeded());
        assertTrue(server.isSecurePortEnabled());
        assertThat(server.getPort(), is(PORT_NR));
        assertTrue(server.isInsecurePortEnabled());
        assertThat(server.getInsecurePort(), is(INSECURE_PORT_NR));
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
        ServiceConfigProperties configProperties = new ServiceConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setKeyStorePath(PREFIX_KEY_PATH + "/honoKeyStore.p12");
        configProperties.setInsecurePort(8888);
        configProperties.setPort(8888);

        // WHEN using this configuration to determine the server's port configuration
        AbstractServiceBase<ServiceConfigProperties> server = createServer(configProperties);
        Future<Void> portConfigurationTracker = server.checkPortConfiguration();

        // THEN port configuration fails
        assertTrue(portConfigurationTracker.failed());
    }
}
