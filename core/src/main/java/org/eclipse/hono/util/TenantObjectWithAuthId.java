/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.util.Objects;

/**
 * Class that keeps a TenantObject and an auth-id.
 */
public final class TenantObjectWithAuthId {

    private final TenantObject tenantObject;
    private final String authId;

    /**
     * Creates a new TenantObjectWithAuthId.
     *
     * @param tenantObject The tenant object.
     * @param authId The identity a device wants to authenticate as. May also be the device id if no authentication is
     *            used.
     * @throws NullPointerException if tenantObject or authId is {@code null}.
     */
    public TenantObjectWithAuthId(final TenantObject tenantObject, final String authId) {
        this.tenantObject = Objects.requireNonNull(tenantObject);
        this.authId = Objects.requireNonNull(authId);
    }

    /**
     * Gets the tenant object.
     *
     * @return The tenant object.
     */
    public TenantObject getTenantObject() {
        return tenantObject;
    }

    /**
     * Gets the identity that a device wants to authenticate as.
     * <p>
     * If no authentication is used, the device id is returned here.
     *
     * @return The authentication id or device id.
     */
    public String getAuthId() {
        return authId;
    }
}
