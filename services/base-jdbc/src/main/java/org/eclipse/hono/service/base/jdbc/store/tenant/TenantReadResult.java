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

package org.eclipse.hono.service.base.jdbc.store.tenant;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.management.tenant.Tenant;

import com.google.common.base.MoreObjects;

/**
 * The result of a tenant read operation.
 */
public class TenantReadResult {

    private final String id;
    private final Tenant tenant;
    private final Optional<String> resourceVersion;

    /**
     * Create a new instance.
     *
     * @param id The tenant ID.
     * @param tenant The tenant information.
     * @param resourceVersion The optional resource version.
     */
    public TenantReadResult(final String id, final Tenant tenant, final Optional<String> resourceVersion) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(resourceVersion);

        this.id = id;
        this.tenant = tenant;
        this.resourceVersion = resourceVersion;
    }

    public Optional<String> getResourceVersion() {
        return this.resourceVersion;
    }

    public Tenant getTenant() {
        return this.tenant;
    }

    public String getId() {
        return this.id;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", this.id)
                .add("resourceVersion", this.resourceVersion)
                .add("tenant", this.tenant)
                .toString();
    }

}
