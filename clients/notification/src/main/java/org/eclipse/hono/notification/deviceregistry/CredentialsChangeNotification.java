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
 * Notification that informs about changes on credentials.
 *
 * The notification only informs that credentials for a device have been changed but not about details of the change.
 * Components that consume this notification could either query the Device Registry to get more information, or assume
 * that the used credentials have become invalid. For example a protocol adapter can simply disconnect the device to
 * enforce re-authentication.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialsChangeNotification extends AbstractNotification {

    public static final String TYPE = "credentials-change-v1";
    public static final String ADDRESS = DeviceChangeNotification.ADDRESS;

    @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true)
    private final String tenantId;

    @JsonProperty(value = NotificationConstants.JSON_FIELD_DEVICE_ID, required = true)
    private final String deviceId;

    @JsonCreator
    CredentialsChangeNotification(
            @JsonProperty(value = NotificationConstants.JSON_FIELD_SOURCE, required = true) final String source,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_CREATION_TIME, required = true) @HonoTimestamp final Instant creationTime,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_TENANT_ID, required = true) final String tenantId,
            @JsonProperty(value = NotificationConstants.JSON_FIELD_DEVICE_ID, required = true) final String deviceId) {

        super(source, creationTime);

        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Creates an instance.
     *
     * @param tenantId The tenant ID of the device.
     * @param deviceId The ID of the device.
     * @param creationTime The creation time of the event.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public CredentialsChangeNotification(final String tenantId, final String deviceId, final Instant creationTime) {
        this(NotificationConstants.SOURCE_DEVICE_REGISTRY, creationTime, tenantId, deviceId);
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

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    public String getAddress() {
        return ADDRESS;
    }

}
