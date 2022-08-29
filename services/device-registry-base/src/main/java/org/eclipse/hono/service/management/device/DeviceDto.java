/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.hono.service.management.BaseDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A Data Transfer Object for device information.
 * <p>
 * This is basically a wrapper around a {@link Device} object, adding a resource version
 * and time stamps for initial creation and last update.
 */
@RegisterForReflection(ignoreNested = false)
public class DeviceDto extends BaseDto<Device> {

    /**
     * The name of the JSON property containing the device data.
     */
    public static final String FIELD_DEVICE = "device";
    /**
     * The name of the JSON property containing the device's identifier.
     */
    public static final String FIELD_DEVICE_ID = "device-id";
    /**
     * The name of the JSON property marking the device as auto-provisioned.
     */
    public static final String FIELD_AUTO_PROVISIONED = "auto-provisioned";
    /**
     * The name of the JSON property indicating whether an auto-provisioning notification has been sent
     * for the device.
     */
    public static final String FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT = "auto-provisioning-notification-sent";

    @JsonProperty(value = FIELD_DEVICE_ID)
    private String deviceId;

    @JsonProperty(FIELD_AUTO_PROVISIONED)
    private boolean autoProvisioned = false;

    @JsonProperty(FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)
    private boolean autoProvisioningNotificationSent = false;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public DeviceDto() {
        // Explicit default constructor.
    }

    /**
     * Creates a DTO for persisting device configuration data.
     *
     * @param supplier The supplier to use for creating the concrete DTO instance.
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param device The device configuration to write to the store.
     * @param version The object's (initial) resource version.
     *
     * @param <T> The concrete type of DTO being created.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static <T extends DeviceDto> T forCreation(
            final Supplier<T> supplier,
            final String tenantId,
            final String deviceId,
            final Device device,
            final String version) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(version);

        final T deviceDto = BaseDto.forCreation(supplier, device.withoutStatus(), version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);
        final Boolean autoProvisioned = Optional.ofNullable(device.getStatus())
                .map(DeviceStatus::isAutoProvisioned)
                .orElse(false);
        deviceDto.setAutoProvisioned(autoProvisioned);

        return deviceDto;
    }

    /**
     * Creates a DTO for device configuration data that has been read from a persistent store.
     *
     * @param supplier The supplier to use for creating the concrete DTO instance.
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param device The device configuration from the store.
     * @param autoProvisioned {@code true} if this device has been provisioned automatically (may be {@code null}).
     * @param notificationSent {@code true} if an auto-provisioning notification has been sent already (may be {@code null}).
     * @param created The point in time when the object was created initially in the store (may be {@code null}).
     * @param updated The point in time when the object was updated most recently in the store (may be {@code null}).
     * @param version The object's resource version in the store (may be {@code null}).
     *
     * @param <T> The type of the DTO subclass.
     *
     * @return The DTO.
     * @throws NullPointerException if any of supplier, tenantId, deviceId and device are {@code null}.
     */
    public static <T extends DeviceDto> T forRead(
            final Supplier<T> supplier,
            final String tenantId,
            final String deviceId,
            final Device device,
            final Boolean autoProvisioned,
            final Boolean notificationSent,
            final Instant created,
            final Instant updated,
            final String version) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(device);

        final T deviceDto = BaseDto.forRead(supplier, device, created, updated, version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);
        deviceDto.setAutoProvisioned(autoProvisioned);
        deviceDto.setAutoProvisioningNotificationSent(notificationSent);

        return deviceDto;
    }

    /**
     * Creates a DTO for updating device configuration data.
     *
     * @param supplier The supplier to use for creating the concrete DTO instance.
     * @param tenantId The identifier of the tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param device The device configuration to write to the store.
     * @param version The new resource version to use for the object in the store.
     *
     * @param <T> The type of the DTO subclass.
     *
     * @return A DTO instance for updating an entry.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static <T extends DeviceDto> T forUpdate(
            final Supplier<T> supplier,
            final String tenantId,
            final String deviceId,
            final Device device,
            final String version) {

        Objects.requireNonNull(supplier);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(version);

        final T deviceDto = BaseDto.forUpdate(supplier, device.withoutStatus(), version);
        deviceDto.setTenantId(tenantId);
        deviceDto.setDeviceId(deviceId);
        final Boolean autoProvisioned = Optional.ofNullable(device.getStatus())
                .map(DeviceStatus::isAutoProvisioned)
                .orElse(false);
        final Boolean notificationSent = Optional.ofNullable(device.getStatus())
                .map(DeviceStatus::isAutoProvisioningNotificationSent)
                .orElse(false);
        deviceDto.setAutoProvisioned(autoProvisioned);
        deviceDto.setAutoProvisioningNotificationSent(notificationSent);

        return deviceDto;
    }

    /**
     * Gets the device configuration data.
     * <p>
     * The object returned will have a {@code null} valued status property.
     * The {@link #getDeviceWithStatus()} method can be used to obtain an object
     * with its status properly set.
     */
    @Override
    @JsonProperty(FIELD_DEVICE)
    public Device getData() {
        return super.getData();
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
     * Checks if this device was created by Hono's auto-provisioning capability.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if this device was auto-provisioned.
     */
    public final boolean isAutoProvisioned() {
        return autoProvisioned;
    }

    /**
     * Sets whether this device has been provisioned automatically.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param autoProvisioned {@code true} if this device has been provisioned automatically.
     */
    protected void setAutoProvisioned(final Boolean autoProvisioned) {
        this.autoProvisioned = Optional.ofNullable(autoProvisioned).orElse(false);
    }

    /**
     * Checks if a notification has been published, indicating that this device has been provisioned automatically.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if the notification has already been sent.
     */
    public final boolean isAutoProvisioningNotificationSent() {
        return autoProvisioningNotificationSent;
    }

    /**
     * Sets whether a notification has been published, indicating that this device has been provisioned automatically.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param notificationSent {@code true} if the notification has already been sent.
     */
    protected void setAutoProvisioningNotificationSent(final Boolean notificationSent) {
        this.autoProvisioningNotificationSent = Optional.ofNullable(notificationSent).orElse(false);
    }

    /**
     * Gets a copy of the device configuration data and the object's status information (if available).
     *
     * @return The device.
     */
    @JsonIgnore
    public final Device getDeviceWithStatus() {
        final Device deviceWithStatus = new Device(getData());
        deviceWithStatus.setStatus(new DeviceStatus()
                .setAutoProvisioned(isAutoProvisioned())
                .setAutoProvisioningNotificationSent(isAutoProvisioningNotificationSent())
                .setCreationTime(getCreationTime())
                .setLastUpdate(getUpdatedOn())
        );
        return deviceWithStatus;
    }
}
