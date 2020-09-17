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
import java.util.Objects;

import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceWithStatus;
import org.eclipse.hono.service.management.device.Status;
import org.eclipse.hono.util.RegistryManagementConstants;

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

    /**
     * Creates a new data transfer object to store device information in mongodb.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param device The device information.
     * @param version The version of tenant to be sent as request header.
     * @throws NullPointerException if any of the parameters except the device are {@code null}
     */
    public DeviceDto(final String tenantId, final String deviceId, final Device device, final String version) {
        setTenantId(tenantId);
        setDeviceId(deviceId);
        setData(device);
        setVersion(version);
        setUpdatedOn(Instant.now());
        setCreationTime(Instant.now());
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
    public void setTenantId(final String tenantId) {
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
    public void setDeviceId(final String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the device information including internal status.
     *
     * @return The device information including internal status or {@code null} if not set.
     */
    public DeviceWithStatus getDeviceWithStatus() {
        final DeviceWithStatus deviceWithStatus = new DeviceWithStatus(getData());
        deviceWithStatus.setStatus(new Status()
                .setCreationTime(getCreationTime())
                .setLastUpdate(getLastUpdate())
        );
        return deviceWithStatus;
    }
}
