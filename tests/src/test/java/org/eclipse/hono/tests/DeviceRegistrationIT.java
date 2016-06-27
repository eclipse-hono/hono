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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Register some devices, send some messages.
 */
public class DeviceRegistrationIT
{
    private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistrationIT.class);

    JmsIntegrationTestSupport client;

    @Before
    public void init() throws Exception {
        client = JmsIntegrationTestSupport.newClient("hono");
    }

    @After
    public void after() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testRegistrationSucceeds() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        client.registerDevice("device12345", message -> {
            if (JmsIntegrationTestSupport.hasStatus(message, 200)) {
                latch.countDown();
            }
        });
        assertTrue("Registration request timed out", latch.await(2, TimeUnit.SECONDS));
    }
}
