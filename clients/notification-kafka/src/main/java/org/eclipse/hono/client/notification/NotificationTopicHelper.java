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

package org.eclipse.hono.client.notification;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

/**
 * Utility methods to determine the Kafka topic for a given type of notifications.
 */
public final class NotificationTopicHelper {

    private NotificationTopicHelper() {
        // prevent instantiation
    }

    /**
     * Gets the topic name to consume notifications from.
     *
     * @param notificationType The class of the notifications to consume.
     * @param <T> The type of notifications to consume.
     * @return The topic name.
     * @throws IllegalArgumentException If the given type is not a known subclass of {@link AbstractNotification}.
     */
    public static <T extends AbstractNotification> String getTopicName(final Class<T> notificationType) {
        final String address;
        if (TenantChangeNotification.class.equals(notificationType)) {
            address = TenantChangeNotification.ADDRESS;
        } else if (DeviceChangeNotification.class.equals(notificationType)) {
            address = DeviceChangeNotification.ADDRESS;
        } else if (CredentialsChangeNotification.class.equals(notificationType)) {
            address = CredentialsChangeNotification.ADDRESS;
        } else {
            throw new IllegalArgumentException("Unknown notification type " + notificationType.getName());
        }

        return getTopicNameForAddress(address);
    }

    private static String getTopicNameForAddress(final String address) {
        return new HonoTopic(HonoTopic.Type.NOTIFICATION, address).toString();
    }
}
