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

package org.eclipse.hono.service.auth.device;

import java.util.Objects;

import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantObjectContainer;

/**
 * Credentials, provided by a device for authentication, along with detailed tenant information.
 */
public class TenantDetailsDeviceCredentials extends AbstractDeviceCredentials implements TenantObjectContainer {

    private final TenantObject tenantObject;
    private final String type;

    /**
     * Creates a new TenantDetailsDeviceCredentials instance.
     *
     * @param tenantObject The tenant details.
     * @param wrappedCredentials The credentials provided by a device for authentication.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public TenantDetailsDeviceCredentials(final TenantObject tenantObject, final DeviceCredentials wrappedCredentials) {
        super(wrappedCredentials.getTenantId(), wrappedCredentials.getAuthId());
        this.tenantObject = Objects.requireNonNull(tenantObject);
        this.type = wrappedCredentials.getType();
    }

    /**
     * Creates a new TenantDetailsDeviceCredentials instance.
     *
     * @param tenantObject The tenant details.
     * @param authId The authentication identifier of the credentials.
     * @param type The type of the credentials.
     * @throws NullPointerException if tenantObject or type is {@code null}.
     */
    public TenantDetailsDeviceCredentials(final TenantObject tenantObject, final String authId, final String type) {
        super(tenantObject.getTenantId(), authId);
        this.tenantObject = tenantObject;
        this.type = Objects.requireNonNull(type);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * Gets the TenantObject for the tenant that the device claims to belong to.
     *
     * @return The TenantObject.
     */
    @Override
    public TenantObject getTenantObject() {
        return tenantObject;
    }
}
