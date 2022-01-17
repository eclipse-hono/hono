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

package org.eclipse.hono.client.notification.kafka;

import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationType;

/**
 * Utility methods to determine the Kafka topic for a given type of notification.
 */
public final class NotificationTopicHelper {

    private NotificationTopicHelper() {
        // prevent instantiation
    }

    /**
     * Gets the topic name for a notification type.
     *
     * @param notificationType The class of the notification.
     * @param <T> The type of notification.
     * @return The topic name.
     */
    public static <T extends AbstractNotification> String getTopicName(final NotificationType<T> notificationType) {
        return new HonoTopic(HonoTopic.Type.NOTIFICATION, notificationType.getAddress()).toString();
    }

}
