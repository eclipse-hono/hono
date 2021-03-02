/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import java.time.Instant;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;

/**
 * Contains all information about a device that is indicating being ready to receive an upstream message.
 */
public final class TimeUntilDisconnectNotification {

    /**
     * Define the maximum time in milliseconds until a disconnect notification is expired to be 10000 days.
     */
    private static final long MAX_EXPIRY_MILLISECONDS = (long) 60 * 60 * 24 * 10000 * 1000;

    private final String tenantId;

    private final String deviceId;

    private final Integer ttd;

    private final Instant readyUntil;

    private final Instant creationTime;

    /**
     * Build a notification object to indicate that a device is ready to receive an upstream message.
     *
     * @param tenantId The identifier of the tenant of the device the notification is constructed for.
     * @param deviceId The id of the device the notification is constructed for.
     * @param ttd The time until the device <em>disconnects</em> again.
     * @param creationTime The Instant that points to when the notification message was created at the client (adapter).
     * @throws NullPointerException If readyUntil is null.
     */
    public TimeUntilDisconnectNotification(final String tenantId, final String deviceId, final Integer ttd,
            final Instant creationTime) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.ttd = ttd;
        this.readyUntil = getReadyUntilInstantFromTtd(ttd, creationTime);
        this.creationTime = creationTime;
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

    /**
     * Creates a notification from an AMQP message.
     * <p>
     * The notification will contain information extracted from the AMQP message
     * as follows:
     * <ul>
     * <li><em>ttd</em> - the value of the {@link MessageHelper#APP_PROPERTY_DEVICE_TTD} application property</li>
     * <li><em>tenantId</em> - the value of the {@link MessageHelper#APP_PROPERTY_TENANT_ID} application property</li>
     * <li><em>deviceId</em> - the value of the {@link MessageHelper#APP_PROPERTY_DEVICE_ID} application property</li>
     * <li><em>creationTime</em> - the value of the message's <em>creation-time</em> property</li>
     * <li><em>readyUntil</em> - the instant until the device will remain connected</li>
     * </ul>
     *
     * @param msg Message that is evaluated.
     * @return A notification if the message contains a
     *         TTD value in its {@link MessageHelper#APP_PROPERTY_DEVICE_TTD}
     *         application property or {@code null} otherwise.
     * @throws NullPointerException If msg is {@code null}.
     */
        public static Optional<TimeUntilDisconnectNotification> fromMessage(final Message msg) {

        final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);

        if (ttd == null) {
            return Optional.empty();
        } else if (ttd == 0 || MessageHelper.isDeviceCurrentlyConnected(msg)) {
            final String tenantId = MessageHelper.getTenantIdAnnotation(msg);
            final String deviceId = MessageHelper.getDeviceId(msg);

            if (tenantId != null && deviceId != null) {
                final Instant creationTime = Instant.ofEpochMilli(msg.getCreationTime());

                final TimeUntilDisconnectNotification notification =
                        new TimeUntilDisconnectNotification(tenantId, deviceId, ttd, creationTime);
                return Optional.of(notification);
            }
        }
        return Optional.empty();
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
}
