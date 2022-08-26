/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.util;

/**
 * Constants &gt; utility methods used throughout the Event API.
 */
public final class EventConstants {

    /**
     * The name of the event endpoint.
     */
    public static final String EVENT_ENDPOINT = "event";

    /**
     * The short name of the event endpoint.
     */
    public static final String EVENT_ENDPOINT_SHORT = "e";

    /**
     * The content type of the <em>connection notification</em> event.
     */
    public static final String EVENT_CONNECTION_NOTIFICATION_CONTENT_TYPE = "application/vnd.eclipse-hono-dc-notification+json";

    /**
     * The content type that is defined for empty events without any payload.
     */
    public static final String CONTENT_TYPE_EMPTY_NOTIFICATION = "application/vnd.eclipse-hono-empty-notification";

    /**
     * The content type of the <em>device provisioning notification</em> event.
     */
    public static final String CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION = "application/vnd.eclipse-hono-device-provisioning-notification";


    /**
     * The registration status of a device.
     */
    public enum RegistrationStatus {
        /**
         * Status indicating a newly registered device.
         */
        NEW
    }

    private EventConstants() {
    }

    /**
     * Checks if a given endpoint name is the Event endpoint.
     *
     * @param ep The name to check.
     * @return {@code true} if the name is either {@link #EVENT_ENDPOINT} or {@link #EVENT_ENDPOINT_SHORT}.
     */
    public static boolean isEventEndpoint(final String ep) {
        return EVENT_ENDPOINT.equals(ep) || EVENT_ENDPOINT_SHORT.equals(ep);
    }

    /**
     * Checks if a given content type is the empty notification type.
     *
     * @param contentType The content type to check.
     * @return {@code true} if the given type is the empty notification type.
     */
    public static boolean isEmptyNotificationType(final String contentType) {
        return CONTENT_TYPE_EMPTY_NOTIFICATION.equals(contentType);
    }
}
