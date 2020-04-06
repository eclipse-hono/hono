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

package org.eclipse.hono.deviceregistry.service.deviceconnection;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * Provides a unique key for a <em>Device Connection</em> entry of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API</a>.
 * It is used for storing and retrieving values from the backend storage and external systems.
 */
public final class DeviceConnectionKey {

    protected String tenantId;
    protected String deviceId;

    /**
     * Create a device connection key.
     * @param tenantId The tenant to use.
     * @param deviceId The device id to use.
     */
    public DeviceConnectionKey(final String tenantId, final String deviceId) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                this.deviceId,
                this.tenantId);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DeviceConnectionKey other = (DeviceConnectionKey) obj;
        return Objects.equals(this.deviceId, other.deviceId) &&
                Objects.equals(this.tenantId, other.tenantId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("tenantId", this.tenantId)
                .add("deviceId", this.deviceId)
                .toString();
    }

}
