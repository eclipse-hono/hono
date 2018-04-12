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

import java.time.Instant;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.DecodeException;

/**
 * Constants used for command readiness notifications of a device.
 */
public final class NotificationDeviceCommandReadyConstants extends NotificationConstants {
    /**
     * Prevent construction.
     */
    private NotificationDeviceCommandReadyConstants() {
    }

    /**
     * JSON field name for specifying the time that a device tries to be ready for receiving messages (e.g. a command).
     */
    public static final String FIELD_AVAILABLE = "avail";

    /**
     * Value for the JSON field name {@link #FIELD_CAUSE} that must be used if a device wants to signal that it is ready to
     * receive a command.
     */
    public static final String VALUE_CAUSE_READY_FOR_COMMAND = "cr";

    /**
     * Create a command-ready event for a device.
     *
     * @param timeToBeReady Time in milliseconds that the device will be ready for receiving a command.
     * @return JsonObject A JsonObject that contains the necessary key-value pairs defined for such an event.
     */
    public static final JsonObject createDeviceCommandReadinessNotification(
            final long timeToBeReady) {
        final JsonObject notificationObject = new JsonObject()
                .put(NotificationConstants.FIELD_SOURCE, NotificationConstants.VALUE_SOURCE_DEVICE)
                .put(NotificationConstants.FIELD_CAUSE, NotificationDeviceCommandReadyConstants.VALUE_CAUSE_READY_FOR_COMMAND)
                .put(NotificationDeviceCommandReadyConstants.FIELD_AVAILABLE, timeToBeReady);
        return notificationObject;
    }

    /**
     * Verify if a deviceNotification event (as a JSON object that is defined in the Event API) is signalling that the
     * device for which it was received should be ready to receive a command.
     * This is evaluated at the point in time this method is invoked.
     *
     * @param deviceNotification JsonObject that is evaluated.
     * @param creationTime The creation time in milliseconds as epoche time.
     * @return Boolean {@code true} if the event signals that the device now should be ready to receive a command, {@code false}
     * otherwise.
     * @throws NullPointerException If deviceNotification is {@code null} or if the body of deviceNotification is null.
     * @throws ClassCastException If deviceNotification has a member of wrong type for {@link NotificationDeviceCommandReadyConstants#FIELD_AVAILABLE}.
     * @throws DecodeException If deviceNotification has a body that does not encode a valid JSON object.
     */
    public static final Boolean isDeviceCurrentlyReadyForCommands(final JsonObject deviceNotification, final long creationTime) {
        final Optional<Long> avail = Optional.ofNullable(deviceNotification.getLong(NotificationDeviceCommandReadyConstants.FIELD_AVAILABLE));

        if (avail.isPresent()) {
            final Instant deviceCommandReadyUntil = Instant.ofEpochMilli(creationTime).plusMillis(avail.get());
            return deviceCommandReadyUntil.isAfter(Instant.now());
        } else {
            // if no time to be ready is set, the device should be ready to always receive commands
            return true;
        }
    }

}
