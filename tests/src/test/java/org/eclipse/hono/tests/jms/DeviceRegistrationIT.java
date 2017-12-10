/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Register some devices, send some messages.
 */
public class DeviceRegistrationIT {

    private static final int DEFAULT_TEST_TIMEOUT = 5000; // ms
    private static final String NON_EXISTING_DEVICE_ID = "NON_EXISTING";

    private static JmsIntegrationTestSupport client;

    private RegistrationTestSupport registration;

    /**
     * Opens a connection to the Device Registry.
     * 
     * @throws Exception if the connection cannot be established.
     */
    @BeforeClass
    public static void connectToDeviceRegistry() throws Exception {
        client = JmsIntegrationTestSupport.newClient(JmsIntegrationTestSupport.HONO_DEVICEREGISTRY, IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);
    }

    /**
     * Creates a registration client for the default tenant.
     * 
     * @throws JMSException if the client cannot be created.
     */
    @Before
    public void init() throws JMSException {
        registration = client.getRegistrationTestSupport();
        registration.createConsumer();
        registration.createProducer();
    }

    /**
     * Closes the registration client.
     * 
     * @throws JMSException if the client cannot be closed.
     */
    @After
    public void after() throws JMSException {
        if (registration != null) {
            registration.close();
        }
    }

    /**
     * Closes the connection to the Device Registry.
     * 
     * @throws JMSException if the connection cannot be closed.
     */
    @AfterClass
    public static void disconnect() throws JMSException {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Verifies that a non-existing device cannot be retrieved from the registry.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testGetFailsForNonExistingDevice() throws Exception {
        registration.retrieve(NON_EXISTING_DEVICE_ID, HTTP_NOT_FOUND).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    /**
     * Verifies that a device can be retrieved by ID once it has been registered.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testRegisterDeviceSucceeds() throws Exception {
        String deviceId = getRandomDeviceId();
        registration.register(deviceId, HTTP_CREATED).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        registration.retrieve(deviceId, HTTP_OK).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    /**
     * Verifies that a device ID can not be registered more than once.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testDuplicateRegistrationFailsWithConflict() throws Exception {
        String deviceId = getRandomDeviceId();
        registration.register(deviceId, HTTP_CREATED).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        registration.register(deviceId, HTTP_CONFLICT).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    /**
     * Verifies that a the device registry issues an assertion for a registered device.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testAssertRegistrationSucceeds() throws Exception {
        String deviceId = getRandomDeviceId();
        registration.register(deviceId, HTTP_CREATED).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        RegistrationResult result = registration.assertRegistration(deviceId, HTTP_OK).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result.getPayload().containsKey(RegistrationConstants.FIELD_ASSERTION));
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    /**
     * Verifies that a device is removed from the device registry once it has been deregistered.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testDeregisterDeviceSucceeds() throws Exception {

        String deviceId = getRandomDeviceId();
        registration.register(deviceId, HTTP_CREATED).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        registration.retrieve(deviceId, HTTP_OK).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        registration.deregister(deviceId, HTTP_NO_CONTENT).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        registration.retrieve(deviceId, HTTP_NOT_FOUND).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    /**
     * Verifies that the registration service returns 404 when trying to deregister a non-existing device.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testDeregisterNonExistingDeviceFailsWithNotFound() throws Exception {

        registration.deregister(NON_EXISTING_DEVICE_ID, HTTP_NOT_FOUND).get(DEFAULT_TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat("Did not receive responses to all requests", registration.getCorrelationHelperSize(), is(0));
    }

    /**
     * Verifies that the Device Registry rejects an unauthorized request to open a receiver
     * link for a tenant.
     * 
     * @throws JMSException if the test succeeds.
     */
    @Test(expected = JMSSecurityException.class)
    public void testOpenReceiverNotAllowedForOtherTenant() throws JMSException {

        final RegistrationTestSupport registrationForOtherTenant = client.getRegistrationTestSupport("someOtherTenant", false);
        registrationForOtherTenant.createConsumer();
    }

    /**
     * Verifies that the Device Registry rejects an unauthorized request to open a sender
     * link for a tenant.
     * 
     * @throws JMSException if the test succeeds.
     */
    @Test(expected = JMSSecurityException.class)
    public void testOpenSenderNotAllowedForOtherTenant() throws JMSException {

        final RegistrationTestSupport registrationForOtherTenant = client.getRegistrationTestSupport("someOtherTenant", false);
        registrationForOtherTenant.createProducer();
    }

    /**
     * Verifies that a client must use a correct <em>reply-to</em> address when opening a link for receiving registration responses.
     * 
     * @throws Exception if the test fails.
     */
    @Test
    public void testOpenReceiverFailsForMalformedReplyToAddress() throws Exception {

        // GIVEN a source address that does not contain a resourceId segment
        final Destination invalidSource = new JmsQueue("registration/" + JmsIntegrationTestSupport.TEST_TENANT_ID);

        // WHEN trying to open a receiver link using the malformed source address
        try {
            registration.createConsumerWithoutListener(invalidSource);
            fail("Should have failed to create consumer");
        } catch (JMSException e) {
            // THEN the attempt fails
        }
    }

    private String getRandomDeviceId() {
        return "testDevice-" + UUID.randomUUID().toString();
    }
}
