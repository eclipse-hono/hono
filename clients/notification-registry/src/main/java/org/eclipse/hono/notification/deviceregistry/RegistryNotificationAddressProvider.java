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

package org.eclipse.hono.notification.deviceregistry;

import org.eclipse.hono.client.notification.NotificationAddressProvider;

/**
 * An address provider for the notification types of the device registry.
 *
 * @param <T> The class of the notification.
 */
public class RegistryNotificationAddressProvider<T extends AbstractDeviceRegistryNotification>
        implements NotificationAddressProvider<T> {

    @Override
    public String apply(final Class<T> notificationClass) {

        if (notificationClass.isAssignableFrom(TenantChangeNotification.class)) {
            return TenantChangeNotification.ADDRESS;
        } else if (notificationClass.isAssignableFrom(DeviceChangeNotification.class)) {
            return DeviceChangeNotification.ADDRESS;
        } else if (notificationClass.isAssignableFrom(CredentialsChangeNotification.class)) {
            return CredentialsChangeNotification.ADDRESS;
        } else {
            throw new IllegalArgumentException("unsupported notification type" + notificationClass.getName());
        }
    }
}
