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

package org.eclipse.hono.deviceregistry.service.tenant;

import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * An abstract base class implementation for {@link TenantManagementService}.
 */
public abstract class AbstractTenantManagementService implements TenantManagementService {

    /**
     * Create a new tenant.
     *
     * @param tenantId The ID of the tenant to create.
     * @param tenantObj The tenant information.
     * @param span The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected abstract Future<OperationResult<Id>> createTenant(String tenantId, Tenant tenantObj, Span span);

    @Override
    public Future<OperationResult<Id>> createTenant(final Optional<String> tenantId, final Tenant tenantObj, final Span span) {
        return createTenant(tenantId.orElseGet(this::createId), tenantObj, span);
    }

    /**
     * Create a new tenant ID.
     * @return The new tenant ID.
     */
    protected String createId() {
        return UUID.randomUUID().toString();
    }
}
