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

import org.apache.qpid.proton.message.Message;

/**
 * Contains all information about a device that is indicating being ready to receive an upstream message.
 */
public final class TimeUntilDisconnectNotification {

    private String tenantId;

    private String deviceId;

    private Instant readyUntil;

    /**
     * Build a notification object to indicate that a device is ready to receive an upstream message.
     *
     * @param tenantId The identifier of the tenant of the device the notification is constructed for.
     * @param deviceId The id of the device the notification is constructed for.
     * @param readyUntil The Instant that determines until when this notification is valid.
     */
    public TimeUntilDisconnectNotification(final String tenantId, final String deviceId, final Instant readyUntil) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.readyUntil = readyUntil;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Instant getReadyUntil() {
        return readyUntil;
    }

    @Override
    public String toString() {
        return "TimeUntilDisconnectNotification{" +
                "tenantId='" + tenantId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", readyUntil=" + readyUntil +
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

        if (MessageHelper.isDeviceCurrentlyConnected(msg)) {
            final String tenantId = MessageHelper.getTenantIdAnnotation(msg);
            final String deviceId = MessageHelper.getDeviceId(msg);

            if (tenantId != null && deviceId != null) {
                final Integer ttd = MessageHelper.getTimeUntilDisconnect(msg);
                final Instant creationTime = Instant.ofEpochMilli(msg.getCreationTime());
                final Instant deviceCommandReadyUntil = creationTime.plusSeconds(ttd);

                final TimeUntilDisconnectNotification notification =
                        new TimeUntilDisconnectNotification(tenantId, deviceId, deviceCommandReadyUntil);
                return Optional.of(notification);
            }
        }

        return Optional.empty();
    }

}
