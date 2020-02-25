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

package org.eclipse.hono.deviceregistry.base.device;

import java.util.Objects;

import org.eclipse.hono.deviceregistry.base.tenant.TenantHandle;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

/**
 * A custom class to be used as key in the backend key-value storage.
 * This uses the unique values of a registration to create a unique key to store the registration
 * details.
 */
public final class DeviceKey {

    private final String tenantId;
    private final String deviceId;

    /**
     * Creates a new RegistrationKey. Used by CacheRegistrationService.
     *
     * @param tenantId the id of the tenant owning the registration key.
     * @param deviceId the id of the device being registered.
     */
    private DeviceKey(final String tenantId, final String deviceId) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public String getDeviceId() {
        return this.deviceId;
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

    private ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("tenantId", this.tenantId)
                .add("deviceId", this.deviceId);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    public static DeviceKey deviceKey(final TenantHandle tenantHandle, final String deviceId) {
        Objects.requireNonNull(tenantHandle);
        Objects.requireNonNull(deviceId);

        return new DeviceKey(tenantHandle.getId(), deviceId);
    }
}
