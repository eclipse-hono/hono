/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.application.client;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.hono.util.MessageHelper;

/**
 * Contains all information about a device that is indicating being ready to receive an upstream message.
 */
public final class TimeUntilDisconnectNotification {

    /**
     * Define the maximum time in milliseconds until a disconnect notification is expired to be 10000 days.
     */
    private static final long MAX_EXPIRY_MILLISECONDS = 60 * 60 * 24 * 10000 * 1000L;

    private final String tenantId;

    private final String deviceId;

    private final Integer ttd;

    private final Instant readyUntil;

    private final Instant creationTime;

    /**
     * Creates a notification object to indicate that a device is ready to receive an upstream message.
     *
     * @param tenantId The identifier of the tenant of the device the notification is constructed for.
     * @param deviceId The id of the device the notification is constructed for.
     * @param ttd The number of seconds after which the device will <em>disconnects</em> again.
     * @param creationTime The point in time at which the notification message has been created.
     * @throws NullPointerException If readyUntil is null.
     */
    public TimeUntilDisconnectNotification(
            final String tenantId,
            final String deviceId,
            final Integer ttd,
            final Instant creationTime) {

        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.ttd = ttd;
        this.creationTime = creationTime;
        this.readyUntil = getReadyUntilInstantFromTtd(ttd, creationTime);
    }

    /**
     * Gets the identifier of the tenant that the device belongs to.
     *
     * @return The identifier.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the identifier of the device that sent the TTD.
     *
     * @return The identifier.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Get a representation of the deviceId scoped to the tenant.
     *
     * @return The combined representation, or {@code null} if the tenantId is {@code null}.
     */
    public String getTenantAndDeviceId() {
        return Optional.ofNullable(tenantId).map(t -> t + "/" + deviceId).orElse(null);
    }

    /**
     * Gets the time period that the device indicated to remain connected.
     *
     * @return The time period in seconds.
     */
    public Integer getTtd() {
        return ttd;
    }

    /**
     * Gets the point in time until which the device will remain connected.
     *
     * @return The point in time.
     */
    public Instant getReadyUntil() {
        return readyUntil;
    }

    /**
     * Gets the point in time that the message has been created at.
     *
     * @return The creation time.
     */
    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public String toString() {
        return new StringBuilder("TimeUntilDisconnectNotification [")
                .append("tenant-id: ").append(tenantId)
                .append(", device-id: ").append(deviceId)
                .append(", ttd: ").append(ttd)
                .append(", readyUntil: ").append(readyUntil)
                .append(", creationTime: ").append(creationTime)
                .append("]").toString();
    }

    private static Instant getReadyUntilInstantFromTtd(final Integer ttd, final Instant startingFrom) {
        if (ttd == MessageHelper.TTD_VALUE_UNLIMITED) {
            return Instant.MAX;
        } else if (startingFrom == null) {
            return Instant.MIN;
        } else {
            return startingFrom.plusSeconds(ttd);
        }
    }

    /**
     * Get the time in milliseconds left from the current time until this notification expires.
     *
     * @return The number of milliseconds until this notification expires, or 0 if the notification is already expired.
     */
    public long getMillisecondsUntilExpiry() {
        if (getReadyUntil().equals(Instant.MAX)) {
            return MAX_EXPIRY_MILLISECONDS;
        } else {
            final long milliseconds = getReadyUntil().minusMillis(Instant.now().toEpochMilli()).toEpochMilli();
            return (milliseconds > 0 ? milliseconds : 0);
        }
    }

    /**
     * Checks if a device is currently connected to a protocol adapter.
     * <p>
     * If this method returns {@code true} an attempt could be made to send a command to the device.
     * <p>
     * This method uses the message's creation time and TTD value to determine the point in time
     * until which the device will remain connected.
     *
     * @param ttd The TTD value.
     * @param creationTime The creation time of the message. If {@code null} the device is considered as disconnected.
     * @return {@code true} if the TTD value contained in the message indicates that the device will
     *         stay connected for some additional time.
     */
    public static boolean isDeviceCurrentlyConnected(final Integer ttd, final Long creationTime) {

        return Optional.ofNullable(ttd).map(ttdValue -> {
            if (ttdValue == MessageHelper.TTD_VALUE_UNLIMITED) {
                return true;
            } else if (ttdValue == 0) {
                return false;
            } else {
                if (creationTime == null) {
                    return false;
                }

                final Instant creationTimeInstant = Instant.ofEpochMilli(creationTime);
                return Instant.now().isBefore(creationTimeInstant.plusSeconds(ttdValue));
            }
        }).orElse(false);
    }
}
