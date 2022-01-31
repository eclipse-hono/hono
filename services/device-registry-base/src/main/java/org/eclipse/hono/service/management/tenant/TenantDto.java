/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.tenant;

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.service.management.BaseDto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A Data Transfer Object for tenant information.
 * <p>
 * This is basically a wrapper around a {@link Tenant} object, adding a resource version
 * and time stamps for initial creation and last update.
 */
@RegisterForReflection(ignoreNested = false)
public final class TenantDto extends BaseDto<Tenant> {

    /**
     * The name of the JSON property containing the tenant data.
     */
    public static final String FIELD_TENANT = "tenant";

    /**
     * Default constructor for serialisation/deserialization.
     */
    public TenantDto() {
        // Explicit default constructor.
    }

    /**
     * Creates a DTO for persisting tenant configuration data.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenant The tenant configuration to write to the store.
     * @param version The object's (initial) resource version.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static TenantDto forCreation(final String tenantId, final Tenant tenant, final String version) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(version);

        final TenantDto tenantDto = BaseDto.forCreation(TenantDto::new, tenant, version);
        tenantDto.setTenantId(tenantId);

        return tenantDto;
    }

    /**
     * Creates a DTO for tenant configuration data that has been read from a persistent store.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenant The tenant configuration from the store.
     * @param created The point in time when the object was created initially in the store (may be {@code null}).
     * @param updated The point in time when the object was updated most recently in the store (may be {@code null}).
     * @param version The object's resource version in the store (may be {@code null}).
     *
     * @return The DTO.
     * @throws NullPointerException if tenant ID or tenant are {@code null}.
     */
    public static TenantDto forRead(
            final String tenantId,
            final Tenant tenant,
            final Instant created,
            final Instant updated,
            final String version) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenant);

        final TenantDto tenantDto = BaseDto.forRead(TenantDto::new, tenant, created, updated, version);
        tenantDto.setTenantId(tenantId);

        return tenantDto;
    }

    /**
     * Creates a DTO for updating tenant configuration data.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenant The tenant configuration to write to the store.
     * @param version The new resource version to use for the object in the store or {@code null} if
     *                the new resource version should be created automatically.
     *
     * @return The DTO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static TenantDto forUpdate(final String tenantId, final Tenant tenant, final String version) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(version);

        final TenantDto tenantDto = BaseDto.forUpdate(TenantDto::new, tenant, version);
        tenantDto.setTenantId(tenantId);

        return tenantDto;
    }

    @Override
    @JsonProperty(FIELD_TENANT)
    public Tenant getData() {
        return super.getData();
    }
}
