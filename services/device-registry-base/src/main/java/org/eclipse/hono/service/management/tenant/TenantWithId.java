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

import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Tenant information which also includes tenant identifier.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class TenantWithId extends Tenant {

    @JsonProperty(RegistryManagementConstants.FIELD_ID)
    private String id;

    /**
     * Empty default constructor.
     * <p>
     * Mainly useful for mapping from JSON.
     */
    private TenantWithId() {
    }

    private TenantWithId(final String id, final Tenant tenant) {
        super(tenant);
        this.id = id;
    }

    /**
     * Creates an instance of {@link TenantWithId} from the given tenant identifier and
     * {@link Tenant} instance.
     *
     * @param id The tenant identifier.
     * @param tenant The tenant information.
     *
     * @return an instance of {@link TenantWithId}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static TenantWithId from(final String id, final Tenant tenant) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenant);

        return new TenantWithId(id, tenant);
    }

    /**
     * Returns the tenant identifier.
     *
     * @return the tenant identifier.
     */
    public String getId() {
        return id;
    }
}
