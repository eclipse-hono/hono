/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.auth;

import java.security.Principal;
import java.util.Objects;

import org.eclipse.hono.util.RequestResponseApiConstants;

import io.vertx.core.json.JsonObject;


/**
 * An authenticated client of a protocol adapter representing a device.
 */
public class Device implements Principal {

    private final JsonObject principal;

    /**
     * Creates a new device for a tenant and device identifier.
     *
     * @param tenantId The tenant.
     * @param deviceId The device identifier.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    public Device(final String tenantId, final String deviceId) {
        super();
        this.principal = getPrincipal(tenantId, deviceId);
    }

    private JsonObject getPrincipal(final String tenantId, final String deviceId) {
        return new JsonObject()
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, Objects.requireNonNull(tenantId))
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID, Objects.requireNonNull(deviceId));
    }

    /**
     * Get the underlying principal of the device.
     *
     * @return JSON representation of the Principal.
     */
    public final JsonObject principal() {
        return principal;
    }

    /**
     * Gets the identifier of the tenant this device belongs to.
     *
     * @return The identifier.
     */
    public final String getTenantId() {
        return principal.getString(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID);
    }

    /**
     * Gets this device's identifier.
     *
     * @return The identifier.
     */
    public final String getDeviceId() {
        return principal.getString(RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the device identifier.
     */
    @Override
    public final String getName() {
        return getDeviceId();
    }

    @Override
    public final String toString() {
        return String.format("device [%s: %s, %s: %s]",
                RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID,
                getDeviceId(),
                RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID,
                getTenantId());
    }

    /**
     * Gets the device id in an address structure.
     *
     * @param tenantId The id of the tenant.
     * @param deviceId The id of the device.
     * @return tenantId and deviceId as an address.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static final String asAddress(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        return String.format("%s/%s", tenantId, deviceId);
    }

    /**
     * Gets the device id in an address structure.
     *
     * @param device The device.
     * @return tenantId and deviceId as an address.
     * @throws NullPointerException if device is {@code null}.
     */
    public static final String asAddress(final Device device) {
        return String.format("%s/%s", device.getTenantId(), device.getDeviceId());
    }
}
