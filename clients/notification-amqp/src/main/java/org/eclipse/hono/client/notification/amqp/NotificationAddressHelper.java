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

package org.eclipse.hono.client.notification.amqp;

import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

/**
 * Utility methods to determine the AMQP address for a given type of notification.
 */
public final class NotificationAddressHelper {

    private static final String TENANT_CHANGE_ADDRESS = addNotificationEndpointPrefix(TenantChangeNotification.ADDRESS);
    private static final String DEVICE_CHANGE_ADDRESS = addNotificationEndpointPrefix(DeviceChangeNotification.ADDRESS);
    private static final String CREDENTIALS_CHANGE_ADDRESS = addNotificationEndpointPrefix(CredentialsChangeNotification.ADDRESS);
    private static final String ALL_DEVICES_OF_TENANT_CHANGE_ADDRESS = addNotificationEndpointPrefix(AllDevicesOfTenantDeletedNotification.ADDRESS);

    private NotificationAddressHelper() {
        // prevent instantiation
    }

    /**
     * Gets the AMQP address for sending or receiving notifications.
     *
     * @param notificationType The class of the notifications.
     * @param <T> The type of notifications.
     * @return The address.
     * @throws IllegalArgumentException If the given type is not a known subclass of {@link AbstractNotification}.
     */
    public static <T extends AbstractNotification> String getAddress(final Class<T> notificationType) {
        final String address;
        if (TenantChangeNotification.class.equals(notificationType)) {
            address = TENANT_CHANGE_ADDRESS;
        } else if (DeviceChangeNotification.class.equals(notificationType)) {
            address = DEVICE_CHANGE_ADDRESS;
        } else if (CredentialsChangeNotification.class.equals(notificationType)) {
            address = CREDENTIALS_CHANGE_ADDRESS;
        } else if (AllDevicesOfTenantDeletedNotification.class.equals(notificationType)) {
            address = ALL_DEVICES_OF_TENANT_CHANGE_ADDRESS;
        } else {
            throw new IllegalArgumentException("Unknown notification type " + notificationType.getName());
        }
        return address;
    }

    private static String addNotificationEndpointPrefix(final String address) {
        return NotificationConstants.NOTIFICATION_ENDPOINT + "/" + address;
    }
}
