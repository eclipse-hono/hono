/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.device;

import java.util.Objects;

import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * Provides a unique key for a <em>Device</em> resource of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It is used for storing and retrieving values from the backend storage and external systems.
 */
public final class DeviceKey {

    private final String tenantId;
    private final String deviceId;

    /**
     * Creates a device key.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     */
    private DeviceKey(final String tenantId, final String deviceId) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The identifier or {@code null} if not set.
     */
    public String getTenantId() {
        return this.tenantId;
    }

    /**
     * Gets the device identifier.
     *
     * @return The identifier or {@code null} if not set.
     */
    public String getDeviceId() {
        return this.deviceId;
    }

    /**
     * Creates a device key from tenant key and device identifier.
     *
     * @param tenantKey The tenant key.
     * @param deviceId The device identifier.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @return The device key.
     */
    public static DeviceKey from(final TenantKey tenantKey, final String deviceId) {
        Objects.requireNonNull(tenantKey);
        Objects.requireNonNull(deviceId);

        return new DeviceKey(tenantKey.getTenantId(), deviceId);
    }

    /**
     * Creates a device key from tenant and device identifiers.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @return The device key.
     */
    public static DeviceKey from(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return new DeviceKey(tenantId, deviceId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeviceKey that = (DeviceKey) o;
        return Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                this.tenantId,
                this.deviceId);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    private ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("tenantId", this.tenantId)
                .add("deviceId", this.deviceId);
    }
}
