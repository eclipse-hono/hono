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
    public static DeviceDto forRead(final String tenantId, final String deviceId, final JsonObject recordJson) {

        return DeviceDto.forRead(tenantId,
            deviceId,
            recordJson.getJsonObject(MongoDbDeviceRegistryUtils.FIELD_DEVICE).mapTo(Device.class),
            new DeviceStatus()
                .setAutoProvisioned(recordJson.getBoolean(MongoDbDeviceRegistryUtils.FIELD_AUTO_PROVISIONED))
                .setAutoProvisioningNotificationSent(recordJson.getBoolean(MongoDbDeviceRegistryUtils.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)),
            recordJson.getInstant(MongoDbDeviceRegistryUtils.FIELD_CREATED),
            recordJson.getInstant(MongoDbDeviceRegistryUtils.FIELD_UPDATED_ON),
            recordJson.getString(MongoDbDeviceRegistryUtils.FIELD_VERSION));
    }

    @Override
    @JsonProperty(MongoDbDeviceRegistryUtils.FIELD_DEVICE)
    public Device getData() {
        return super.getData();
    }
}
