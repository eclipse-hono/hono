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

import org.eclipse.hono.service.management.BaseDto;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A versioned and dated wrapper class for {@link Tenant}.
 */
public final class TenantDto extends BaseDto<Tenant> {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public TenantDto() {
        // Explicit default constructor.
    }

    private TenantDto(final Tenant data, final Instant created, final Instant updated, final String version) {
        super(data, created, updated, version);
    }

    /**
     * Constructs a new DTO for use with the <b>creation of a new</b> persistent entry.
     *
     * @param tenantId The id of the tenant.
     * @param tenant The data of the DTO.
     * @param version The version of the DTO
     *
     * @return A DTO instance for creating a new entry.
     */
    public static TenantDto forCreation(final String tenantId, final Tenant tenant, final String version) {
        final TenantDto tenantDto = new TenantDto(tenant, Instant.now(), null, version);
        tenantDto.setTenantId(tenantId);

        return tenantDto;
    }

    /**
     * Constructs a new DTO to be returned by a read operation.
     *
     * @param tenantId The id of the tenant.
     * @param tenant The data of the DTO.
     * @param created The instant when the object was created.
     * @param updated The instant of the most recent update.
     * @param version The version of the DTO
     *
     * @return A DTO instance for reading an entry.
     */
    public static TenantDto forRead(final String tenantId, final Tenant tenant, final Instant created, final Instant updated, final String version) {
        final TenantDto tenantDto = new TenantDto(tenant, created, updated, version);
        tenantDto.setTenantId(tenantId);

        return tenantDto;
    }

    /**
     * Constructs a new DTO for <b>updating</b> a persistent entry.
     *
     * @param tenantId The id of the tenant.
     * @param tenant The data of the DTO.
     * @param version The version of the DTO
     *
     * @return A DTO instance for updating an entry.
     */
    public static TenantDto forUpdate(final String tenantId, final Tenant tenant, final String version) {
        final TenantDto tenantDto = new TenantDto(tenant, null, Instant.now(), version);
        tenantDto.setTenantId(tenantId);

        return tenantDto;
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
    private void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    @Override
    @JsonProperty(RegistryManagementConstants.FIELD_TENANT)
    public Tenant getData() {
        return super.getData();
    }
}
