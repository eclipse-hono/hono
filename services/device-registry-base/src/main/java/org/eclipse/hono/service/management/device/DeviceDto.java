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
package org.eclipse.hono.service.management.device;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A base class for a device DTO.
 */
public class DeviceDto extends BaseDto<Device> {
    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID)
    private String deviceId;

    @JsonUnwrapped
    private DeviceStatus deviceStatus = new DeviceStatus();

    /**
     * Default constructor for serialisation/deserialization.
     */
    public DeviceDto() {
        // Explicit default constructor.
    }

    /**
     * Constructs a new DTO for use with the <b>creation of a new</b> persistent entry.
     *
     * @param supplier A DTO subclass' constructor of which a new instance shall be created.
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param autoProvisioned Marks this device as being auto-provisioned.
     * @param device The data of the DTO.
     * @param version The version of the DTO
     *
     * @param <T> The type of the DTO subclass.
     *
     * @return A DTO instance for creating a new entry.
     */
    public static <T extends DeviceDto> T forCreation(final Supplier<T> supplier, final String tenantId, final String deviceId, final Boolean autoProvisioned, final Device device, final String version) {
        final T deviceDto = BaseDto.forCreation(supplier,
                withoutStatus(device),
                version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);
        deviceDto.setDeviceStatus(new DeviceStatus()
                .setAutoProvisioned(autoProvisioned)
        );

        return deviceDto;
    }

    /**
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param device The data of the DTO.
     * @param deviceStatus The registry internal status of the device.
     * @param created The instant when the object was created.
     * @param updated The instant of the most recent update.
     * @param version The version of the DTO
     *
     * @return A DTO instance for reading an entry.
     */
    public static DeviceDto forRead(final String tenantId, final String deviceId, final Device device,
                                    final DeviceStatus deviceStatus, final Instant created, final Instant updated, final String version) {
        final DeviceDto deviceDto = BaseDto.forRead(DeviceDto::new, device, created, updated, version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);
        deviceDto.setDeviceStatus(deviceStatus);

        return deviceDto;
    }

    /**
     * Constructs a new DTO for use with the <b>updating</b> a persistent entry.
     *
     * @param supplier A DTO subclass' constructor of which a new instance shall be created.
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @param autoProvisioningNotificationSent Marks the auto-provisioning notification for this device as sent.
     * @param device The data of the DTO.
     * @param version The version of the DTO
     *
     * @param <T> The type of the DTO subclass.
     *
     * @return A DTO instance for updating an entry.
     */
    public static <T extends DeviceDto> T forUpdate(final Supplier<T> supplier, final String tenantId, final String deviceId, final Boolean autoProvisioningNotificationSent, final Device device, final String version) {
        final T deviceDto = BaseDto.forUpdate(supplier, withoutStatus(device), version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);
        deviceDto.setDeviceStatus(new DeviceStatus()
                .setAutoProvisioningNotificationSent(autoProvisioningNotificationSent)
        );

        return deviceDto;
    }

    /**
     * Returns a new device without internal status.
     * <p>
     * The status should be null anyway, since it should not be deserialized in the given device value object.
     * Also it will be overwritten with the actual internal status when devices are retrieved.
     * Nevertheless this makes sure that status information will never be persisted.
     *
     * @param device The device which should be copied without status.
     *
     * @return The copied device.
     */
    protected static Device withoutStatus(final Device device) {
        return new Device(device).setStatus(null);
    }

    /**
     * Gets the identifier of the tenant.
     *
     * @return The identifier of the tenant.
     */
    public final String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the identifier of the tenant.
     *
     * @param tenantId The tenant's identifier.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    protected final void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the identifier of the device.
     *
     * @return The identifier of the device.
     */
    public final String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the identifier of the device.
     *
     * @param deviceId The identifier of the device.
     * @throws NullPointerException if the deviceId is {@code null}.
     */
    protected final void setDeviceId(final String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the registry internal status information of this device.
     *
     * @return The registry internal status information.
     */
    public final DeviceStatus getDeviceStatus() {
        return deviceStatus;
    }

    /**
     * Sets the registry internal status information of this device.
     *
     * @param deviceStatus The status information to be set.
     */
    public final void setDeviceStatus(final DeviceStatus deviceStatus) {
        Objects.requireNonNull(deviceStatus);

        this.deviceStatus = deviceStatus;
    }

    /**
     * Gets the device information including internal status.
     *
     * @return The device information including internal status or {@code null} if not set.
     */
    @JsonIgnore
    public Device getDeviceWithStatus() {
        final Device deviceWithStatus = new Device(getData());
        deviceWithStatus.setStatus(new DeviceStatus()
                .setAutoProvisioned(getDeviceStatus().isAutoProvisioned())
                .setAutoProvisioningNotificationSent(getDeviceStatus().isAutoProvisioningNotificationSent())
                .setCreationTime(getCreationTime())
                .setLastUpdate(getUpdatedOn())
        );
        return deviceWithStatus;
    }
}
