/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.model;

import java.util.Objects;

import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonObject;

/**
 * A DTO (Data Transfer Object) class to store device information in mongodb.
 */
public final class MongoDbBasedDeviceDto extends DeviceDto {

    /**
     * Default constructor for serialisation/deserialization.
     */
    public MongoDbBasedDeviceDto() {
        // Explicit default constructor.
    }

    /**
     * Creates a DTO for device configuration data that has been read from a persistent store.
     *
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param deviceData The JSON document representing the device data.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static MongoDbBasedDeviceDto forRead(final String tenantId, final String deviceId, final JsonObject deviceData) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);

        return DeviceDto.forRead(
                MongoDbBasedDeviceDto::new,
                tenantId,
                deviceId,
                deviceData.getJsonObject(FIELD_DEVICE).mapTo(Device.class),
                new DeviceStatus()
                        .setAutoProvisioned(deviceData.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                        .setAutoProvisioningNotificationSent(deviceData
                                .getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)),
                deviceData.getInstant(MongoDbBasedDeviceDto.FIELD_CREATED),
                deviceData.getInstant(MongoDbBasedDeviceDto.FIELD_UPDATED_ON),
                deviceData.getString(MongoDbBasedDeviceDto.FIELD_VERSION));
    }

    /**
     * Returns the value of the auto-provisioned flag.
     *
     * @return the auto-provisioned flag.
     */
    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONED)
    public boolean isAutoProvisioned() {
        return getDeviceStatus().isAutoProvisioned();
    }

    /**
     * Returns the value of the auto-provisioning-notification-sent flag.
     * Required
     *
     * @return the auto-provisioning-notification-sent flag.
     */
    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)
    public boolean isAutoProvisioningNotificationSent() {
        return getDeviceStatus().isAutoProvisioningNotificationSent();
    }
}
