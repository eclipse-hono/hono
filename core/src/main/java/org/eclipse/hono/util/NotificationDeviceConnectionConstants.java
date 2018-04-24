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

import io.vertx.core.json.JsonObject;

/**
 * Constants used for command readiness notifications of a device.
 */
public final class NotificationDeviceConnectionConstants extends NotificationConstants {
    /**
     * Prevent construction.
     */
    private NotificationDeviceConnectionConstants() {
    }

    /**
     * Value for the JSON field name {@link #FIELD_CAUSE} to notify that a device connected to Hono.
     */
    public static final String VALUE_CAUSE_DEVICE_CONNECTED = "connected";

    /**
     * Value for the JSON field name {@link #FIELD_CAUSE} to notify that a device disconnected from Hono.
     */
    public static final String VALUE_CAUSE_DEVICE_DISCONNECTED = "disconnected";

    /**
     * Create a command-ready event for a device.
     *
     * @param source The source of this notification (e.g. the type of protocol adapter).
     * @param cause The cause of sending this notification.
     * @return JsonObject A JsonObject that contains the necessary key-value pairs defined for such an event.
     */
    public static final JsonObject createDeviceConnectionNotification(
            final String source, final String cause) {
        final JsonObject notificationObject = new JsonObject()
                .put(NotificationConstants.FIELD_SOURCE, source)
                .put(NotificationConstants.FIELD_CAUSE, cause);
        return notificationObject;
    }

}
