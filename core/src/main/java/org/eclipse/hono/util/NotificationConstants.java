/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import org.apache.qpid.proton.message.Message;

import java.util.Arrays;
import java.util.Objects;

/**
 * Constants used for notification events.
 */
public abstract class NotificationConstants {

    /**
     * The content type that is defined for device command readiness notification events.
     */
    public static final String CONTENT_TYPE_DEVICE_COMMAND_READINESS_NOTIFICATION = "application/vnd.eclipse-hono-dcr-notification+json";

    /**
     * The content type that is defined for device connection notification events.
     */
    public static final String CONTENT_TYPE_DEVICE_CONNECTION_NOTIFICATION = "application/vnd.eclipse-hono-dc-notification+json";

    /**
     * Enum that defines all valid notifications by their content-type.
     */
    public enum NotificationContentType {
        DEVICE_COMMAND_READINESS_NOTIFICATION(CONTENT_TYPE_DEVICE_COMMAND_READINESS_NOTIFICATION),
        DEVICE_CONNECTION_NOTIFICATION(CONTENT_TYPE_DEVICE_CONNECTION_NOTIFICATION);

        private final String contentType;

        NotificationContentType(final String contentType) {
            this.contentType = contentType;
        }

        /**
         * Helper method to check if a subject is a valid content type.
         *
         * @param subject The subject to validate.
         * @return boolean {@code true} if the subject denotes a valid action, {@code false} otherwise.
         */
        public static boolean isValid(final String subject) {
            return Arrays.stream(NotificationContentType.values()).filter(type -> type.contentType.equals(subject)).findFirst().isPresent();
        }
    }

    /**
     * JSON field name for specifying the source of the event.
     */
    public static final String FIELD_SOURCE = "src";
    /**
     * Value for the JSON field name {@link #FIELD_SOURCE} that must be used if a device sends a device notification event itself.
     */
    public static final String VALUE_SOURCE_DEVICE = "dev";
    /**
     * JSON field name for specifying the cause of the event.
     */
    public static final String FIELD_CAUSE = "cause";
    /**
     * JSON field name for additional data that can contain a JSON object for arbitrary purpose.
     * Any content using this key is not evaluated by Hono anywhere.
     */
    public static final String FIELD_ADDITIONAL_DATA = "data";

    /**
     * Validates if the passed event message is of one the defined content types for notifications.
     *
     * @param eventMessage The message to investigate for being a notification.
     * @return boolean {@code true} if the message is a notification, {@code false} otherwise.
     */
    public static final boolean isNotification(final Message eventMessage) {
        Objects.requireNonNull(eventMessage);

        return NotificationContentType.isValid(eventMessage.getContentType());
    }
}
