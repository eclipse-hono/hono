/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.management.tenant.TenantWithId;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * An abstract base class implementation for {@link TenantManagementService}.
 */
public abstract class AbstractTenantManagementService implements TenantManagementService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Creates a new Tenant.
     * <p>
     * This method is invoked by {@link #createTenant(Optional, Tenant, Span)} after all parameter checks
     * have succeeded.
     *
     * @param tenantId The identifier of the tenant to create.
     * @param tenantObj The configuration information to add for the tenant.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the created tenant's identifier if the tenant
     *         has been created successfully. The result's <em>status</em> property will have a value as specified
     *         in the Device Registry Management API.
     */
    protected abstract Future<OperationResult<Id>> processCreateTenant(String tenantId, Tenant tenantObj, Span span);

    /**
     * Reads tenant configuration information for a tenant identifier.
     * <p>
     * This method is invoked by {@link #readTenant(String, Span)} after all parameter checks
     * have succeeded.
     *
     * @param tenantId The identifier of the tenant.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the retrieved tenant information if a tenant
     *         with the given identifier exists. The result's <em>status</em> property will have a value as specified
     *         in the Device Registry Management API.
     */
    protected abstract Future<OperationResult<Tenant>> processReadTenant(String tenantId, Span span);

    /**
     * Updates an existing tenant.
     * <p>
     * This method is invoked by {@link #updateTenant(String, Tenant, Optional, Span)} after all parameter checks
     * have succeeded.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantObj The updated configuration information for the tenant (may be {@code null}).
     * @param resourceVersion The resource version that the tenant instance is required to have.
     *                        If empty, the resource version of the tenant instance on record will be ignored.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The result's <em>status</em> property will have a value as specified
     *         in the Device Registry Management API.
     */
    protected abstract Future<OperationResult<Void>> processUpdateTenant(
            String tenantId,
            Tenant tenantObj,
            Optional<String> resourceVersion,
            Span span);

    /**
     * Finds tenants with optional filters, paging and sorting options.
     * <p>
     * This method is invoked by {@link #searchTenants(int, int, List, List, Span)} after all parameter checks
     * have succeeded.
     * <p>
     * This default implementation returns a future failed with a {@link org.eclipse.hono.client.ServerErrorException}
     * having a {@link HttpURLConnection#HTTP_NOT_IMPLEMENTED} status code.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *                   retrieve the whole result set page by page.
     * @param filters A list of filters. The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options. The sortOptions specify properties to sort the result set by.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the matching tenants. The result's <em>status</em>
     *         property will have a value as specified in the Device Registry Management API.
     */
    protected Future<OperationResult<SearchResult<TenantWithId>>> processSearchTenants(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        return Future.failedFuture(new ServerErrorException(
                HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                "this implementation does not support the search tenamts operation"));
    }

    /**
     * Deletes a tenant.
     * <p>
     * This method is invoked by {@link #deleteTenant(String, Optional, Span)} after all parameter checks
     * have succeeded.
     *
     * @param tenantId The identifier of the tenant.
     * @param resourceVersion The resource version that the tenant instance is required to have.
     *                        If empty, the resource version of the tenant instance on record will be ignored.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The result's <em>status</em> property will have a value as specified
     *         in the Device Registry Management API.
     */
    protected abstract Future<Result<Void>> processDeleteTenant(String tenantId, Optional<String> resourceVersion, Span span);

    @Override
    public final Future<OperationResult<Id>> createTenant(
            final Optional<String> tenantId,
            final Tenant tenantObj,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(span);

        final Promise<Void> tenantCheck = Promise.promise();
        try {
            tenantObj.assertTrustAnchorIdUniquenessAndCreateMissingIds();
            tenantCheck.complete();
        } catch (final IllegalStateException e) {
            log.debug("error creating tenant", e);
            TracingHelper.logError(span, e);
            tenantCheck.fail(new ClientErrorException(
                    tenantId.get(),
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    e.getMessage()));
        }

        return tenantCheck.future()
                .compose(ok -> processCreateTenant(tenantId.orElseGet(this::createId), tenantObj, span))
                .otherwise(t -> DeviceRegistryUtils.mapErrorToResult(t, span));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<OperationResult<Tenant>> readTenant(final String tenantId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return processReadTenant(tenantId, span)
                .otherwise(t -> DeviceRegistryUtils.mapErrorToResult(t, span));
    }

    @Override
    public final Future<OperationResult<Void>> updateTenant(
            final String tenantId,
            final Tenant tenantObj,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        final Promise<Void> tenantCheck = Promise.promise();
        try {
            tenantObj.assertTrustAnchorIdUniquenessAndCreateMissingIds();
            tenantCheck.complete();
        } catch (final IllegalStateException e) {
            log.debug("error updating tenant", e);
            TracingHelper.logError(span, e);
            tenantCheck.fail(new ClientErrorException(
                    tenantId,
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    e.getMessage()));
        }
        return tenantCheck.future()
                .compose(ok -> processUpdateTenant(tenantId, tenantObj, resourceVersion, span))
                .otherwise(t -> DeviceRegistryUtils.mapErrorToResult(t, span));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<Result<Void>> deleteTenant(
            final String tenantId,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return processDeleteTenant(tenantId, resourceVersion, span)
                .otherwise(t -> DeviceRegistryUtils.mapErrorToResult(t, span));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<OperationResult<SearchResult<TenantWithId>>> searchTenants(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(span);

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        return processSearchTenants(pageSize, pageOffset, filters, sortOptions, span)
                .otherwise(t -> DeviceRegistryUtils.mapErrorToResult(t, span));
    }

    /**
     * Creates a new identifier.
     * <p>
     * This default implementation creates a new UUID on each invocation.
     *
     * @return The ID.
     */
    protected String createId() {
        return DeviceRegistryUtils.getUniqueIdentifier();
    }
}
