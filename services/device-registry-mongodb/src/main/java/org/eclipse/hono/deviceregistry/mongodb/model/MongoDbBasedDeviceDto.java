/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
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
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param recordJson The JSON of the device's MongoDb document.
     *
     * @return A DTO instance for reading an entry.
     */
    public static MongoDbBasedDeviceDto forRead(final String tenantId, final String deviceId, final JsonObject recordJson) {

        return DeviceDto.forRead(MongoDbBasedDeviceDto::new, tenantId,
                deviceId,
                recordJson.getJsonObject(MongoDbDeviceRegistryUtils.FIELD_DEVICE).mapTo(Device.class),
                new DeviceStatus()
                        .setAutoProvisioned(recordJson.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED))
                        .setAutoProvisioningNotificationSent(recordJson
                                .getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)),
                recordJson.getInstant(MongoDbDeviceRegistryUtils.FIELD_CREATED),
                recordJson.getInstant(MongoDbDeviceRegistryUtils.FIELD_UPDATED_ON),
                recordJson.getString(MongoDbDeviceRegistryUtils.FIELD_VERSION));
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

    @Override
    @JsonProperty(MongoDbDeviceRegistryUtils.FIELD_DEVICE)
    public Device getData() {
        return super.getData();
    }
}
