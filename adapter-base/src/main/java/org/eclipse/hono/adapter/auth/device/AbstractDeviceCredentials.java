/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

/**
 * A base class providing utility methods for verifying credentials.
 *
 */
public abstract class AbstractDeviceCredentials implements DeviceCredentials {

    private final String tenantId;
    private final String authId;
    private final JsonObject clientContext;

    /**
     * Creates credentials for a tenant and authentication identifier.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The identifier that the device uses for authentication.
     */
    protected AbstractDeviceCredentials(final String tenantId, final String authId) {
        this(tenantId, authId, new JsonObject());
    }

    /**
     * Creates credentials for a tenant and authentication identifier.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param authId The identifier that the device uses for authentication.
     * @param clientContext The client context that can be used to get credentials from the Credentials API.
     * @throws NullPointerException if clientContext is {@code null}.
     */
    protected AbstractDeviceCredentials(final String tenantId, final String authId, final JsonObject clientContext) {
        this.tenantId = tenantId;
        this.authId = authId;
        this.clientContext = Objects.requireNonNull(clientContext);
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

    /**
     * Gets additional properties that can be used to get credentials from the Credentials API.
     *
     * @return The client context, not null.
     */
    @Override
    public final JsonObject getClientContext() {
        return clientContext;
    }
}
