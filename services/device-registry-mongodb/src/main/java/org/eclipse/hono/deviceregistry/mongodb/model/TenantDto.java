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

package org.eclipse.hono.deviceregistry.mongodb.model;

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A versioned and dated wrapper class for {@link Tenant}.
 */
public final class TenantDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;
    @JsonProperty(RegistryManagementConstants.FIELD_TENANT)
    private Tenant tenant;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public TenantDto() {
        // Explicit default constructor.
    }

    /**
     * Creates a tenant DTO from tenant id, a version, an updated timestamp and a {@link Tenant}.
     * 
     * @param tenantId The tenant id.
     * @param tenant The tenant.
     * @param version The version of tenant to be sent as request header.
     */
    public TenantDto(final String tenantId, final Tenant tenant, final String version) {
        setTenantId(tenantId);
        setVersion(version);
        setTenant(tenant);
        setUpdatedOn(Instant.now());
    }

    /**
     * Gets the tenant id.
     *
     * @return the tenant id or {@code null} if none has been set.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant id.
     *
     * @param tenantId the tenant id.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    public void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the {@link Tenant}.
     *
     * @return the tenant or {@code null} if none has been set.
     */
    public Tenant getTenant() {
        return tenant;
    }

    /**
     * Sets the {@link Tenant}.
     *
     * @param tenant the tenant.
     */
    public void setTenant(final Tenant tenant) {
        this.tenant = tenant;
    }

}
