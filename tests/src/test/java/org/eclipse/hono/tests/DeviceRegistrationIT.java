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
 */
package org.eclipse.hono.tests;

import static org.junit.Assert.assertTrue;
import static org.eclipse.hono.tests.JmsIntegrationTestSupport.*;

import java.net.HttpURLConnection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies that a device can be registered with Hono using a JMS based client.
 */
public class DeviceRegistrationIT
{
    private static final String DEVICE_ID = "device12345";

    private static JmsIntegrationTestSupport client;

    @BeforeClass
    public static void init() throws Exception {
        client = newClient(HONO);
    }

    @AfterClass
    public static void after() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testRegistrationSucceeds() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        client.registerDevice(DEVICE_ID, result -> {
            if (hasStatus(result, HttpURLConnection.HTTP_OK)) {
                latch.countDown();
            }
        });
        assertTrue("Registration request timed out", latch.await(5, TimeUnit.SECONDS));
    }
}
