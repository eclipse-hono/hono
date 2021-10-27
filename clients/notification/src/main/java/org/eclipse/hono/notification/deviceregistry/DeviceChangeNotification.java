/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notification that informs about changes on a device.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceChangeNotification extends AbstractNotification {

    public static final String TYPE = "device-change-v1";
    public static final String ADDRESS = "registry-device";

    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE, required = true)
    private final LifecycleChange change;

    @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true)
    private final String tenantId;

    @JsonProperty(value = NotificationConstants.JSON_FIELD_DEVICE_ID, required = true)
    private final String deviceId;

    @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED, required = true)
    private final boolean enabled;

    @JsonCreator
    DeviceChangeNotification(
            @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE, required = true) final String source,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_CREATION_TIME, required = true) @HonoTimestamp final Instant creationTime,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_CHANGE, required = true) final LifecycleChange change,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true) final String tenantId,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DEVICE_ID, required = true) final String deviceId,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DATA_ENABLED, required = true) final boolean enabled) {

        super(source, creationTime);

        this.change = Objects.requireNonNull(change);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.enabled = enabled;
    }

    /**
     * Creates an instance.
     *
     * @param change The type of change to notify about.
     * @param tenantId The tenant ID of the device.
     * @param deviceId The ID of the device.
     * @param creationTime The creation time of the event.
     * @param enabled {@code true} if the device is enabled.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public DeviceChangeNotification(final LifecycleChange change, final String tenantId, final String deviceId,
            final Instant creationTime, final boolean enabled) {
        this(NotificationConstants.SOURCE_DEVICE_REGISTRY, creationTime, change, tenantId, deviceId, enabled);
    }

    /**
     * Gets the change that caused the notification.
     *
     * @return The change.
     */
    public final LifecycleChange getChange() {
        return change;
    }

    /**
     * Gets the tenant ID of the changed device.
     *
     * @return The tenant ID.
     */
    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the ID of the changed device.
     *
     * @return The device ID.
     */
    public final String getDeviceId() {
        return deviceId;
    }

    /**
     * Checks if the device is enabled.
     *
     * @return {@code true} if this device is enabled.
     */
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    public String getAddress() {
        return ADDRESS;
    }

}
