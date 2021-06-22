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
package org.eclipse.hono.service.base.jdbc.store.model;

import java.util.UUID;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceStatus;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * A DTO (Data Transfer Object) class to store device information via JDBC.
 */
public class JdbcBasedDeviceDto extends DeviceDto {

    /**
     * Constructs a new DTO for use with the <b>creation of a new</b> persistent entry.
     *
     * @param deviceKey The key of the device.
     * @param device The data of the DTO.
     *
     * @return A DTO instance for creating a new entry.
     */
    public static JdbcBasedDeviceDto forCreation(final DeviceKey deviceKey, final Device device) {

        return DeviceDto.forCreation(JdbcBasedDeviceDto::new, deviceKey.getTenantId(),
                deviceKey.getDeviceId(),
                device,
                UUID.randomUUID().toString());
    }

    /**
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param recordJson The JSON of the device's data record.
     *
     * @return A DTO instance for reading an entry.
     */
    public static JdbcBasedDeviceDto forRead(final String tenantId, final String deviceId, final JsonObject recordJson) {

        return DeviceDto.forRead(
                JdbcBasedDeviceDto::new,
                tenantId,
                deviceId,
                Json.decodeValue(recordJson.getString("data"), Device.class),
                new DeviceStatus()
                    .setAutoProvisioned(recordJson.getBoolean("auto_provisioned"))
                    .setAutoProvisioningNotificationSent(recordJson.getBoolean("auto_provisioning_notification_sent")),
                recordJson.getInstant("created"),
                recordJson.getInstant("updated_on"),
                recordJson.getString("version"));
    }

    /**
     * Constructs a new DTO for use with the <b>updating</b> a persistent entry.
     *
     * @param deviceKey The key of the device.
     * @param device The data of the DTO.
     *
     * @return A DTO instance for updating an entry.
     */
    public static JdbcBasedDeviceDto forUpdate(final DeviceKey deviceKey, final Device device) {

        return DeviceDto.forUpdate(JdbcBasedDeviceDto::new,
                deviceKey.getTenantId(),
                deviceKey.getDeviceId(),
                device,
                UUID.randomUUID().toString());
    }

    public String getDeviceJson() {
        return Json.encode(getData());
    }
}
