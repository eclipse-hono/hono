/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
import java.util.Objects;
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

    private String tenantId;

    private String deviceId;

    private Integer ttd;

    private Instant readyUntil;

    private Instant creationTime;

    /**
     * Build a notification object to indicate that a device is ready to receive an upstream message.
     *
     * @param tenantId The identifier of the tenant of the device the notification is constructed for.
     * @param deviceId The id of the device the notification is constructed for.
     * @param ttd The time until the device <em>disconnects</em> again.
     * @param readyUntil The Instant that determines until when this notification is valid.
     * @param creationTime The Instant that points to when the notification message was created at the client (adapter).
     * @throws NullPointerException If readyUntil is null.
     */
    private TimeUntilDisconnectNotification(final String tenantId, final String deviceId, final Integer ttd,
                                            final Instant readyUntil, final Instant creationTime) {
        Objects.requireNonNull(readyUntil);

        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.ttd = ttd;
        this.readyUntil = readyUntil;
        this.creationTime = creationTime;
    }

    public String getTenantId() {
        return tenantId;
    }

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

    public Integer getTtd() {
        return ttd;
    }

    public Instant getReadyUntil() {
        return readyUntil;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    @Override
    public String toString() {
        return "TimeUntilDisconnectNotification{" +
                "tenantId='" + tenantId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", ttd='" + ttd + '\'' +
                ", readyUntil='" + readyUntil + '\'' +
                ", creationTime='" + creationTime + '\'' +
                '}';
    }

    /**
     * Provide an instance of {@link TimeUntilDisconnectNotification} if a message indicates that the device sending it
     * is currently connected to a protocol adapter.
     * <p>
     * If this is not the case, the returned {@link Optional} will be empty.
     *
     * @param msg Message that is evaluated.
     * @return Optional containing an instance of the class {@link TimeUntilDisconnectNotification} if the device is considered
     * being ready to receive an upstream message or is empty otherwise.
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
                        new TimeUntilDisconnectNotification(tenantId, deviceId, ttd,
                                getReadyUntilInstantFromTtd(ttd, creationTime), creationTime);
                return Optional.of(notification);
            }
        }
        return Optional.empty();
    }

    private static Instant getReadyUntilInstantFromTtd(final Integer ttd, final Instant startingFrom) {
        if (ttd == MessageHelper.TTD_VALUE_UNLIMITED) {
            return Instant.MAX;
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
