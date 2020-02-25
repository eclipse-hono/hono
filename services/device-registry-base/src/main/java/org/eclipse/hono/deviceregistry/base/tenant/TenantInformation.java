/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.base.tenant;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.management.tenant.Tenant;

import com.google.common.base.MoreObjects.ToStringHelper;

public class TenantInformation extends TenantHandle {

    private final Optional<Tenant> tenant;

    private final String namespace;
    private final String projectName;

    protected TenantInformation(final String namespace, final String projectName, final String id, final Tenant tenant) {
        super(namespace + "." + projectName, id);
        this.namespace = namespace;
        this.projectName = projectName;
        this.tenant = Optional.ofNullable(tenant);
    }

    public Optional<Tenant> getTenant() {
        return this.tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getProjectName() {
        return projectName;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("tenant", this.tenant);
    }

    public static TenantInformation of(final String namespace, final String projectName, final String id, final Tenant tenant) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(projectName);
        Objects.requireNonNull(id);
        return new TenantInformation(namespace, projectName, id, tenant);
    }

}
