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

package org.eclipse.hono.notification.deviceregistry;

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.NotificationType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Notification that informs about changes on a device.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DeviceChangeNotification extends AbstractNotification {

    public static final String TYPE_NAME = "device-change-v1";
    public static final String ADDRESS = "registry-device";
    public static final NotificationType<DeviceChangeNotification> TYPE = new NotificationType<>(
            TYPE_NAME,
            DeviceChangeNotification.class,
            ADDRESS);

    private final LifecycleChange change;
    private final String tenantId;
    private final String deviceId;
    private final boolean deviceEnabled;

    @JsonCreator
    DeviceChangeNotification(
            @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE, required = true)
            final String source,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_CREATION_TIME, required = true)
            @HonoTimestamp
            final Instant creationTime,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE, required = true)
            final LifecycleChange change,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true)
            final String tenantId,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DEVICE_ID, required = true)
            final String deviceId,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED, required = true)
            final boolean deviceEnabled) {

        super(source, creationTime);

        this.change = Objects.requireNonNull(change);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.deviceEnabled = deviceEnabled;
    }

    /**
     * Creates an instance.
     *
     * @param change The type of change to notify about.
     * @param tenantId The tenant ID of the device.
     * @param deviceId The ID of the device.
     * @param creationTime The creation time of the event.
     * @param deviceEnabled {@code true} if the device is enabled.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public DeviceChangeNotification(
            final LifecycleChange change,
            final String tenantId,
            final String deviceId,
            final Instant creationTime,
            final boolean deviceEnabled) {
        this(NotificationConstants.SOURCE_DEVICE_REGISTRY, creationTime, change, tenantId, deviceId, deviceEnabled);
    }

    /**
     * Gets the change that caused the notification.
     *
     * @return The change.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE)
    public LifecycleChange getChange() {
        return change;
    }

    /**
     * Gets the tenant ID of the changed device.
     *
     * @return The tenant ID.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID)
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the ID of the changed device.
     *
     * @return The device ID.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DEVICE_ID)
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Checks if the device is enabled.
     *
     * @return {@code true} if this device is enabled.
     */
    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED)
    public boolean isDeviceEnabled() {
        return deviceEnabled;
    }

    @Override
    @JsonIgnore
    public NotificationType<DeviceChangeNotification> getType() {
        return TYPE;
    }

    @Override
    @JsonIgnore
    public String getKey() {
        return getDeviceId();
    }

    @Override
    public String toString() {
        return new StringBuilder("DeviceChangeNotification{")
                .append("change=").append(change)
                .append(", tenantId='").append(tenantId).append('\'')
                .append(", deviceId='").append(deviceId).append('\'')
                .append(", deviceEnabled=").append(deviceEnabled)
                .append(", creationTime='").append(getCreationTime()).append('\'')
                .append(", source='").append(getSource()).append("'}")
                .toString();
    }
}
