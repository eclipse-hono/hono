/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.tests.jms;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSSecurityException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register some devices, send some messages.
 */
public class DeviceRegistrationIT  {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationIT.class);
    private static final String TEST_DEVICE_ID = "testDevice-" + UUID.randomUUID().toString();

    private RegistrationTestSupport registration;
    private JmsIntegrationTestSupport client;

    @Before
    public void init() throws Exception {
        client = JmsIntegrationTestSupport.newClient("hono");
        registration = client.getRegistrationTestSupport();
        registration.createConsumer();
        registration.createProducer();
    }

    @After
    public void after() throws Exception {
        if (registration != null) {
            registration.close();
        }
        if (client != null) {
            LOG.debug("closing JMS connection...");
            client.close();
        }
    }

    @Test
    public void testRegisterDevice() throws Exception {
        registration
                .retrieve(TEST_DEVICE_ID, HTTP_NOT_FOUND)
                .thenRun(() -> registration.register(TEST_DEVICE_ID, HTTP_OK))
                .thenRun(() -> registration.register(TEST_DEVICE_ID, HTTP_CONFLICT))
                .thenRun(() -> registration.retrieve(TEST_DEVICE_ID, HTTP_OK))
                .thenRun(() -> registration.deregister(TEST_DEVICE_ID, HTTP_OK))
                .thenRun(() -> registration.retrieve(TEST_DEVICE_ID, HTTP_NOT_FOUND))
                .thenRun(() -> registration.deregister(TEST_DEVICE_ID, HTTP_NOT_FOUND))
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testOpenReceiverNotAllowedForOtherTenant() throws Exception {

        final RegistrationTestSupport registrationForOtherTenant = client.getRegistrationTestSupport("someOtherTenant", false);
        try {
            registrationForOtherTenant.createConsumer();
            Assert.fail("Expected exception, but it worked...");
        } catch (final JMSSecurityException e) {
            LOG.info("Caught expected exception.", e);
        }
    }

    @Test
    public void testOpenSenderNotAllowedForOtherTenant() throws Exception {

        final RegistrationTestSupport registrationForOtherTenant = client.getRegistrationTestSupport("someOtherTenant", false);
        try {
            registrationForOtherTenant.createProducer();
            Assert.fail("Expected exception, but it worked...");
        } catch (final JMSSecurityException e) {
            LOG.info("Caught expected exception.", e);
        }
    }
}
