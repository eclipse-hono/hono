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


package org.eclipse.hono.application.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;

/**
 * Tests TimeUntilDisconnectNotification.
 */
public class TimeUntilDisconnectNotificationTest {

    /**
     * Verifies that the notification is constructed if the ttd value is set to a positive number of seconds.
     */
    @Test
    public void testNotificationIsConstructedIfTtdIsSetToPositiveValue() {

        final var notification = new TimeUntilDisconnectNotification(
                Constants.DEFAULT_TENANT,
                "4711",
                10,
                Instant.now());
        assertNotificationProperties(notification);
        assertEquals(Integer.valueOf(10), notification.getTtd());
    }

    /**
     * Verifies that the notification is constructed if the ttd value is set to a positive number of seconds.
     */
    @Test
    public void testNotificationIsConstructedIfTtdIsSetToUnlimited() {

        final var notification = new TimeUntilDisconnectNotification(
                Constants.DEFAULT_TENANT,
                "4711",
                -1,
                Instant.now());

        assertNotificationProperties(notification);
        assertEquals(notification.getTtd(), Integer.valueOf(-1));
    }

    private void assertNotificationProperties(final TimeUntilDisconnectNotification notification) {
        assertTrue(notification.getMillisecondsUntilExpiry() > 0);
        assertTrue(Constants.DEFAULT_TENANT.equals(notification.getTenantId()));
        assertTrue("4711".equals(notification.getDeviceId()));
    }
}
