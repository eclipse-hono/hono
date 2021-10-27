/*
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

package org.eclipse.hono.deviceregistry.notification;

import static com.google.common.truth.Truth.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link NotificationFactory}.
 */
public class NotificationFactoryTest {

    private static final String TENANT_ID = "my-tenant";
    private static final String DEVICE_ID = "my-device";
    private static final Instant TIMESTAMP = Instant.parse("2007-12-03T10:15:30Z");
    private static final boolean ENABLED = false;

    private static Clock clock;

    /**
     * Sets up a fixed clock.
     */
    @BeforeAll
    public static void setUp() {
        clock = Clock.fixed(TIMESTAMP, ZoneId.of("UTC"));
    }

    /**
     * Verifies that the expected notification is created by {@link NotificationFactory#tenantCreated(String, boolean)}.
     */
    @Test
    public void testTenantCreated() {
        final TenantChangeNotification notification = new NotificationFactory(clock).tenantCreated(TENANT_ID,
                ENABLED);

        assertThat(notification.getChange()).isEqualTo(LifecycleChange.CREATE);
        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(notification.isEnabled()).isEqualTo(ENABLED);
    }

    /**
     * Verifies that the expected notification is created by {@link NotificationFactory#tenantChanged(String, boolean)}.
     */
    @Test
    public void testTenantChanged() {
        final TenantChangeNotification notification = new NotificationFactory(clock).tenantChanged(TENANT_ID,
                ENABLED);

        assertThat(notification.getChange()).isEqualTo(LifecycleChange.UPDATE);
        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(notification.isEnabled()).isEqualTo(ENABLED);
    }

    /**
     * Verifies that the expected notification is created by {@link NotificationFactory#tenantDeleted(String)}.
     */
    @Test
    public void testTenantDeleted() {
        final TenantChangeNotification notification = new NotificationFactory(clock).tenantDeleted(TENANT_ID);

        assertThat(notification.getChange()).isEqualTo(LifecycleChange.DELETE);
        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    /**
     * Verifies that the expected notification is created by
     * {@link NotificationFactory#deviceCreated(String, String, boolean)}.
     */
    @Test
    public void testDeviceCreated() {
        final DeviceChangeNotification notification = new NotificationFactory(clock).deviceCreated(TENANT_ID, DEVICE_ID,
                ENABLED);

        assertThat(notification.getChange()).isEqualTo(LifecycleChange.CREATE);
        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(notification.isEnabled()).isEqualTo(ENABLED);
    }

    /**
     * Verifies that the expected notification is created by
     * {@link NotificationFactory#deviceChanged(String, String, boolean)}.
     */
    @Test
    public void testDeviceChanged() {
        final DeviceChangeNotification notification = new NotificationFactory(clock).deviceChanged(TENANT_ID, DEVICE_ID,
                ENABLED);

        assertThat(notification.getChange()).isEqualTo(LifecycleChange.UPDATE);
        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(notification.isEnabled()).isEqualTo(ENABLED);
    }

    /**
     * Verifies that the expected notification is created by {@link NotificationFactory#deviceDeleted(String, String)}.
     */
    @Test
    public void testDeviceDeleted() {
        final DeviceChangeNotification notification = new NotificationFactory(clock).deviceDeleted(TENANT_ID,
                DEVICE_ID);

        assertThat(notification.getChange()).isEqualTo(LifecycleChange.DELETE);
        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    /**
     * Verifies that the expected notification is created by
     * {@link NotificationFactory#credentialsChanged(String, String)}.
     */
    @Test
    public void testCredentialsChanged() {
        final CredentialsChangeNotification notification = new NotificationFactory(clock).credentialsChanged(TENANT_ID,
                DEVICE_ID);

        assertThat(notification.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(notification.getDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(notification.getTimestamp()).isEqualTo(TIMESTAMP);
    }

}
