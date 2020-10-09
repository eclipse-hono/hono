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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.Status;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A DTO (Data Transfer Object) class to store device information in mongodb.
 */
public final class DeviceDto extends BaseDto<Device> {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
    private String deviceId;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public DeviceDto() {
        // Explicit default constructor.
    }

    private DeviceDto(final Device data, final Instant created, final Instant updated, final String version) {
        super(data, created, updated, version);
    }

    /**
     * Constructs a new DTO for use with the <b>creation of a new</b> persistent entry.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param device The data of the DTO.
     * @param version The version of the DTO
     *
     * @return A DTO instance for creating a new entry.
     */
    public static DeviceDto forCreation(final String tenantId, final String deviceId, final Device device, final String version) {
        final DeviceDto deviceDto = new DeviceDto(
                withoutStatus(device),
                Instant.now(),
                null,
                version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);

        return deviceDto;
    }

    /**
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param device The data of the DTO.
     * @param created The instant when the object was created.
     * @param updated The instant of the most recent update.
     * @param version The version of the DTO
     *
     * @return A DTO instance for reading an entry.
     */
    public static DeviceDto forRead(final String tenantId, final String deviceId, final Device device,
                                    final Instant created, final Instant updated, final String version) {
        final DeviceDto deviceDto = new DeviceDto(
                withoutStatus(device),
                created,
                updated,
                version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);

        return deviceDto;
    }

    /**
     * Constructs a new DTO for use with the <b>updating</b> a persistent entry.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param device The data of the DTO.
     * @param version The version of the DTO
     *
     * @return A DTO instance for updating an entry.
     */
    public static DeviceDto forUpdate(final String tenantId, final String deviceId, final Device device, final String version) {
        final DeviceDto deviceDto = new DeviceDto(
                withoutStatus(device),
                null,
                Instant.now(),
                version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);

        return deviceDto;
    }

    /**
     * Returns a new device without internal status.
     * <br><br>
     * The status should be null anyway, since it should not be deserialized in the given device value object.
     * Also it will be overwritten with the actual internal status when devices are retrieved.
     * Nevertheless this makes sure that status information will never be persisted.
     *
     * @param device The device which should be copied without status.
     *
     * @return The copied device.
     */
    private static Device withoutStatus(final Device device) {
        return new Device(device).setStatus(null);
    }

    @Override
    @JsonProperty(MongoDbDeviceRegistryUtils.FIELD_DEVICE)
    public Device getData() {
        return super.getData();
    }

    /**
     * Gets the identifier of the tenant.
     *
     * @return The identifier of the tenant.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the identifier of the tenant.
     *
     * @param tenantId The tenant's identifier.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    private void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the identifier of the device.
     *
     * @return The identifier of the device.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the identifier of the device.
     *
     * @param deviceId The identifier of the device.
     * @throws NullPointerException if the deviceId is {@code null}.
     */
    private void setDeviceId(final String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the device information including internal status.
     *
     * @return The device information including internal status or {@code null} if not set.
     */
    @JsonIgnore
    public Device getDeviceWithStatus() {
        final Device deviceWithStatus = new Device(getData());
        deviceWithStatus.setStatus(new Status()
                .setCreationTime(getCreationTime())
                .setLastUpdate(getUpdatedOn())
        );
        return deviceWithStatus;
    }
}
