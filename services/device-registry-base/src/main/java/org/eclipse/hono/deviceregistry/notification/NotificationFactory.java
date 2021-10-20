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

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

/**
 * A factory that creates notifications that inform about events happening in the Device Registry.
 *
 * The factory adds the point in time when the notification is created as the timestamp. The notification should be
 * created as soon as possible/reasonable after the event happened.
 */
public class NotificationFactory {

    private final Clock clock;

    /**
     * Creates an instance.
     *
     * Notifications created by this instance will use the system clock to create timestamps.
     */
    public NotificationFactory() {
        clock = Clock.systemUTC();
    }

    // visible for testing
    NotificationFactory(final Clock clock) {
        this.clock = clock;
    }

    /**
     * Creates a notification to inform about the creation of a tenant.
     *
     * @param tenantId The ID of the created tenant.
     * @param enabled {@code true} if the tenant is enabled.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId is {@code null}.
     */
    public TenantChangeNotification tenantCreated(final String tenantId, final boolean enabled) {
        Objects.requireNonNull(tenantId);

        return new TenantChangeNotification(LifecycleChange.CREATE, tenantId, Instant.now(clock), enabled);
    }

    /**
     * Creates a notification to inform about the update of a tenant.
     *
     * @param tenantId The ID of the changed tenant.
     * @param enabled {@code true} if the tenant is enabled.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId is {@code null}.
     */
    public TenantChangeNotification tenantChanged(final String tenantId, final boolean enabled) {
        Objects.requireNonNull(tenantId);

        return new TenantChangeNotification(LifecycleChange.UPDATE, tenantId, Instant.now(clock), enabled);
    }

    /**
     * Creates a notification to inform about the deletion of a tenant.
     *
     * @param tenantId The ID of the deleted tenant.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId is {@code null}.
     */
    public TenantChangeNotification tenantDeleted(final String tenantId) {
        Objects.requireNonNull(tenantId);

        return new TenantChangeNotification(LifecycleChange.DELETE, tenantId, Instant.now(clock), false);
    }

    /**
     * Creates a notification to inform about the creation of a device.
     *
     * @param tenantId The tenant ID of the created device.
     * @param deviceId The ID of the created device.
     * @param enabled {@code true} if the device is enabled.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId or deviceId are {@code null}.
     */
    public DeviceChangeNotification deviceCreated(final String tenantId, final String deviceId,
            final boolean enabled) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return new DeviceChangeNotification(LifecycleChange.CREATE, tenantId, deviceId, Instant.now(clock), enabled);
    }

    /**
     * Creates a notification to inform about the update of a device.
     *
     * @param tenantId The tenant ID of the changed device.
     * @param deviceId The ID of the changed device.
     * @param enabled {@code true} if the device is enabled.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId or deviceId are {@code null}.
     */
    public DeviceChangeNotification deviceChanged(final String tenantId, final String deviceId,
            final boolean enabled) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return new DeviceChangeNotification(LifecycleChange.UPDATE, tenantId, deviceId, Instant.now(clock), enabled);
    }

    /**
     * Creates a notification to inform about the deletion of a device.
     *
     * @param tenantId The tenant ID of the deleted device.
     * @param deviceId The ID of the deleted device.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId or deviceId are {@code null}.
     */
    public DeviceChangeNotification deviceDeleted(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return new DeviceChangeNotification(LifecycleChange.DELETE, tenantId, deviceId, Instant.now(clock), false);
    }

    /**
     * Creates a notification to inform about the update of credentials.
     *
     * @param tenantId The tenant ID of the changed credentials.
     * @param deviceId The device ID of the changed credentials.
     * @return The notification with the current timestamp.
     * @throws NullPointerException If tenantId or deviceId are {@code null}.
     */
    public CredentialsChangeNotification credentialsChanged(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return new CredentialsChangeNotification(tenantId, deviceId, Instant.now(clock));
    }

}
