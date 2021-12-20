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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceDto;

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
     * @param version The object's (initial) resource version.
     *
     * @return A DTO instance for creating a new entry.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static JdbcBasedDeviceDto forCreation(
            final DeviceKey deviceKey,
            final Device device,
            final String version) {

        Objects.requireNonNull(deviceKey);
        Objects.requireNonNull(device);
        Objects.requireNonNull(version);

        return DeviceDto.forCreation(
                JdbcBasedDeviceDto::new,
                deviceKey.getTenantId(),
                deviceKey.getDeviceId(),
                device,
                version);
    }

    /**
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param recordJson The JSON of the device's data record.
     *
     * @return A DTO instance for reading an entry.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static JdbcBasedDeviceDto forRead(final String tenantId, final String deviceId, final JsonObject recordJson) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(recordJson);

        final var device = Json.decodeValue(recordJson.getString("data"), Device.class);
        return DeviceDto.forRead(
                JdbcBasedDeviceDto::new,
                tenantId,
                deviceId,
                device,
                recordJson.getBoolean("auto_provisioned"),
                recordJson.getBoolean("auto_provisioning_notification_sent"),
                readInstant(recordJson.getString("created")),
                readInstant(recordJson.getString("updated_on")),
                recordJson.getString("version"));
    }

    private static Instant readInstant(final String value) {
        return Optional.ofNullable(value)
                .map(LocalDateTime::parse)
                .map(ldt -> ldt.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    /**
     * Constructs a new DTO for use with the <b>updating</b> a persistent entry.
     *
     * @param deviceKey The key of the device.
     * @param device The data of the DTO.
     * @param version The new resource version to use for the object in the store.
     *
     * @return A DTO instance for updating an entry.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static JdbcBasedDeviceDto forUpdate(
            final DeviceKey deviceKey,
            final Device device,
            final String version) {

        Objects.requireNonNull(deviceKey);
        Objects.requireNonNull(device);
        Objects.requireNonNull(version);

        return DeviceDto.forUpdate(
                JdbcBasedDeviceDto::new,
                deviceKey.getTenantId(),
                deviceKey.getDeviceId(),
                device,
                version);
    }

    public String getDeviceJson() {
        return Json.encode(getData());
    }
}
