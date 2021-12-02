/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.service.tenant;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/**
 * Provides a unique key for a <em>Tenant</em> resource of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It is used for storing and retrieving values from the backend storage and external systems.
 */
public final class TenantKey {

    private final String tenantId;
    private final String name;


    /**
     * Creates a tenant key.
     *
     * @param tenantId The tenant identifier.
     * @param name The tenant name in an external system.
     */
    public TenantKey(final String tenantId, final String name) {
        this.tenantId = tenantId;
        this.name = name;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The identifier or {@code null} if not set.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the tenant name in external system.
     * It is used for mapping of the tenant id to the logical name in the external system (if applicable).
     *
     * @return The name or {@code null} if not set.
     */
    public String getName() {
        return name;
    }

    /**
     * Creates a tenant key from tenant identifier.
     *
     * @param tenantId The tenant identifier.
     * @throws NullPointerException if provided tenant identifier is {@code null}.
     * @return The tenant key.
     */
    public static TenantKey from(final String tenantId) {
        Objects.requireNonNull(tenantId);
        return new TenantKey(tenantId, null);
    }

    /**
     * Creates a tenant key from tenant identifier and ad tenant name in external system.
     *
     * @param tenantId The tenant identifier.
     * @param name The tenant name in an external system.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @return The tenant key.
     */
    public static TenantKey from(final String tenantId, final String name) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(name);
        return new TenantKey(tenantId, name);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantKey)) {
            return false;
        }
        final TenantKey tenantKey = (TenantKey) o;
        return Objects.equals(tenantId, tenantKey.tenantId) &&
                Objects.equals(name, tenantKey.name);
    }


    @Override
    public int hashCode() {
        return Objects.hash(name, tenantId);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    private MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("name", this.name)
                .add("id", this.tenantId);
    }
}
