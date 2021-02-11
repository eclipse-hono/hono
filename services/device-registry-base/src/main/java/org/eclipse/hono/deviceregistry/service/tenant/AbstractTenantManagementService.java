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

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * An abstract base class implementation for {@link TenantManagementService}.
 */
public abstract class AbstractTenantManagementService implements TenantManagementService {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Create a new tenant.
     *
     * @param tenantId The ID of the tenant to create.
     * @param tenantObj The tenant information.
     * @param span The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected abstract Future<OperationResult<Id>> processCreateTenant(String tenantId, Tenant tenantObj, Span span);

    /**
     * Updates an existing tenant.
     *
     * @param tenantId The ID of the tenant to create.
     * @param tenantObj The tenant information.
     * @param resourceVersion The identifier of the resource version to update.
     * @param span The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected abstract Future<OperationResult<Void>> processUpdateTenant(String tenantId, Tenant tenantObj,
            Optional<String> resourceVersion, Span span);

    @Override
    public Future<OperationResult<Id>> createTenant(final Optional<String> tenantId, final Tenant tenantObj,
            final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(span);

        try {
            tenantObj.assertTrustAnchorIdUniquenessAndCreateMissingIds();
        } catch (final IllegalStateException e) {
            log.debug("error creating tenant", e);
            TracingHelper.logError(span, e);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST));
        }

        return processCreateTenant(tenantId.orElseGet(this::createId), tenantObj, span);
    }

    @Override public Future<OperationResult<Void>> updateTenant(final String tenantId, final Tenant tenantObj,
            final Optional<String> resourceVersion, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        try {
            tenantObj.assertTrustAnchorIdUniquenessAndCreateMissingIds();
        } catch (final IllegalStateException e) {
            log.debug("error updating tenant", e);
            TracingHelper.logError(span, e);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return processUpdateTenant(tenantId, tenantObj, resourceVersion, span);
    }

    /**
     * Create a new tenant ID.
     * @return The new tenant ID.
     */
    protected String createId() {
        return DeviceRegistryUtils.getUniqueIdentifier();
    }

}
