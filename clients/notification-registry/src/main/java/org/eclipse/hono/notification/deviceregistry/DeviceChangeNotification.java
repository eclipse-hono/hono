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
import org.eclipse.hono.client.notification.NotificationConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notification that informs about changes on a device.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceChangeNotification extends AbstractDeviceRegistryNotification {

    public static final String TYPE = "device-change-v1";
    public static final String ADDRESS = "registry-device";

    @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_DATA_CHANGE, required = true)
    private LifecycleChange change;

    @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_DEVICE_ID, required = true)
    private String deviceId;

    @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_DATA_ENABLED, required = true)
    private boolean enabled;

    @JsonCreator
    DeviceChangeNotification(
            @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE, required = true) final String source,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_TIMESTAMP, required = true) @HonoTimestamp final Instant timestamp,
            @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_DATA_CHANGE, required = true) final LifecycleChange change,
            @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_TENANT_ID, required = true) final String tenantId,
            @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_DEVICE_ID, required = true) final String deviceId,
            @JsonProperty(value = RegistryNotificationConstants.JSON_FIELD_DATA_ENABLED, required = true) final boolean enabled) {

        super(source, timestamp);

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
     * @param timestamp The timestamp of the event (Unix epoch, UTC, in milliseconds).
     * @param enabled {@code true} if the device is enabled.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public DeviceChangeNotification(final LifecycleChange change, final String tenantId, final String deviceId,
            final Instant timestamp, final boolean enabled) {
        this(RegistryNotificationConstants.SOURCE_DEVICE_REGISTRY, timestamp, change, tenantId, deviceId, enabled);
    }

    /**
     * Gets the change that caused the notification.
     *
     * @return The change.
     */
    public LifecycleChange getChange() {
        return change;
    }

    /**
     * Gets the tenant ID of the changed device.
     *
     * @return The tenant ID.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the ID of the changed device.
     *
     * @return The device ID.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Checks if the device is enabled.
     *
     * @return {@code true} if this device is enabled.
     */
    public boolean isEnabled() {
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
