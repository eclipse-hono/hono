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

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.tests.jms.JmsIntegrationTestSupport.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.naming.NamingException;

import org.apache.qpid.jms.JmsQueue;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register some devices, send some messages.
 */
public class DeviceRegistrationIT {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationIT.class);
    private static final String TEST_DEVICE_ID = "testDevice-" + UUID.randomUUID().toString();
    private static JmsIntegrationTestSupport client;
    private RegistrationTestSupport registration;

    @BeforeClass
    public static void connectToDeviceRegistry() throws JMSException, NamingException {
        client = JmsIntegrationTestSupport.newClient(HONO_DEVICEREGISTRY, HONO_USER, HONO_PASSWORD);
    }

    @Before
    public void init() throws Exception {
        registration = client.getRegistrationTestSupport();
        registration.createConsumer();
        registration.createProducer();
    }

    @After
    public void after() throws Exception {
        if (registration != null) {
            registration.close();
        }
    }

    @AfterClass
    public static void disconnect() throws JMSException {
        if (client != null) {
            LOG.debug("closing JMS connection...");
            client.close();
        }
    }

    @Test
    public void testGetFailsForNonExistingDevice() throws Exception {
        registration.retrieve(TEST_DEVICE_ID, HTTP_NOT_FOUND).get(2, TimeUnit.SECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    @Test
    public void testRegisterDeviceSucceeds() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        registration.register(deviceId, HTTP_CREATED).get(2, TimeUnit.SECONDS);
        registration.retrieve(deviceId, HTTP_OK).get(2, TimeUnit.SECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    @Test
    public void testDuplicateRegistrationFailsWithConflict() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        registration.register(deviceId, HTTP_CREATED).get(2, TimeUnit.SECONDS);
        registration.register(deviceId, HTTP_CONFLICT).get(2, TimeUnit.SECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    @Test
    public void testAssertRegistrationSucceeds() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        registration.register(deviceId, HTTP_CREATED).get(2, TimeUnit.SECONDS);
        registration.assertRegistration(deviceId, HTTP_OK).get(2, TimeUnit.SECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    @Test
    public void testDeregisterDeviceSucceeds() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        registration.register(deviceId, HTTP_CREATED).get(2, TimeUnit.SECONDS);
        registration.retrieve(deviceId, HTTP_OK).get(2, TimeUnit.SECONDS);
        registration.deregister(deviceId, HTTP_OK).get(2, TimeUnit.SECONDS);
        registration.retrieve(deviceId, HTTP_NOT_FOUND).get(2, TimeUnit.SECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    @Test
    public void testDeregisterNonExistingDeviceFailsWithNotFound() throws Exception {
        registration.deregister("NON_EXISTING", HTTP_NOT_FOUND).get(2, TimeUnit.SECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    @Test(expected = JMSSecurityException.class)
    public void testOpenReceiverNotAllowedForOtherTenant() throws Exception {

        final RegistrationTestSupport registrationForOtherTenant = client.getRegistrationTestSupport("someOtherTenant", false);
        registrationForOtherTenant.createConsumer();
    }

    @Test(expected = JMSSecurityException.class)
    public void testOpenSenderNotAllowedForOtherTenant() throws Exception {

        final RegistrationTestSupport registrationForOtherTenant = client.getRegistrationTestSupport("someOtherTenant", false);
        registrationForOtherTenant.createProducer();
    }

    @Test(expected = JMSException.class)
    public void testOpenReceiverWithInvalidReplyAddress() throws Exception {
        final Destination invalid = new JmsQueue("registration/" + TEST_TENANT_ID);
        registration.createConsumerWithoutListener(invalid);
    }
}
