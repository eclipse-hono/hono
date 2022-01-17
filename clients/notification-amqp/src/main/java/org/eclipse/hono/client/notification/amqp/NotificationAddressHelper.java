/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.notification.NotificationType;

/**
 * Utility methods to determine the AMQP address for a given type of notification.
 */
public final class NotificationAddressHelper {

    private NotificationAddressHelper() {
        // prevent instantiation
    }

    /**
     * Gets the AMQP address for sending or receiving notifications.
     *
     * @param notificationType The class of the notifications.
     * @param <T> The type of notifications.
     * @return The address.
     */
    public static <T extends AbstractNotification> String getAddress(final NotificationType<T> notificationType) {
        return NotificationConstants.NOTIFICATION_ENDPOINT + "/" + notificationType.getAddress();
    }

}
