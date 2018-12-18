/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

/**
 * A base class providing utility methods for verifying credentials.
 *
 */
public abstract class AbstractDeviceCredentials implements DeviceCredentials {

    private final String tenantId;
    private final String authId;

    /**
     * Creates credentials for a tenant and authentication identifier.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param authId The identifier that the device uses for authentication.
     */
    protected AbstractDeviceCredentials(final String tenantId, final String authId) {
        this.tenantId = tenantId;
        this.authId = authId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getAuthId() {
        return authId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getTenantId() {
        return tenantId;
    }
}
