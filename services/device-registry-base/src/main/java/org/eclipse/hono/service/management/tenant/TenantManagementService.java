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

package org.eclipse.hono.service.management.tenant;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A service for managing tenant information.
 * <p>
 * The methods defined by this interface represent the <em>tenant</em> resources
 * of Hono's <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 */
public interface TenantManagementService {

    /**
     * Creates a new Tenant.
     *
     * @param tenantId The identifier of the tenant to create. If empty, the service implementation
     *                 will create an identifier for the tenant.
     * @param tenantObj The configuration information to add for the tenant.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the created tenant's identifier if the tenant
     *         has been created successfully. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/createTenant">
     *      Device Registry Management API - Create Tenant</a>
     */
    Future<OperationResult<Id>> createTenant(Optional<String> tenantId, Tenant tenantObj, Span span);

    /**
     * Reads tenant configuration information for a tenant identifier.
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
     *         with the given identifier exists. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/getTenant">
     *      Device Registry Management API - Get Tenant</a>
     */
    Future<OperationResult<Tenant>> readTenant(String tenantId, Span span);

    /**
     * Finds tenants for search criteria.
     * <p>
     * This operation is considered optional since it is not required for the normal functioning of Hono and
     * is more of a convenient operation.
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
     *         The future will be succeeded with a result containing the matching tenants. Otherwise, the future will
     *         be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code
     *         as specified in the Device Registry Management API.
     * @throws NullPointerException if any of filters, sort options or tracing span are {@code null}.
     * @throws IllegalArgumentException if page size is &lt;= 0 or page offset is &lt; 0.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/searchTenants"> Device Registry
     *      Management API - Search Tenants</a>
     */
    default Future<OperationResult<SearchResult<TenantWithId>>> searchTenants(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        return Future.failedFuture(new ServerErrorException(
                HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                "this implementation does not support the search tenants operation"));
    }

    /**
     * Updates configuration information of a tenant.
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
     *         The future will be succeeded if a tenant matching the criteria exists and has been updated successfully.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/updateTenant">
     *      Device Registry Management API - Update Tenant</a>
     */
    Future<OperationResult<Void>> updateTenant(
            String tenantId,
            Tenant tenantObj,
            Optional<String> resourceVersion,
            Span span);

    /**
     * Deletes a tenant.
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
     *         The future will be succeeded if a tenant matching the criteria exists and has been deleted successfully.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/deleteTenant">
     *      Device Registry Management API - Delete Tenant</a>
     */
    Future<Result<Void>> deleteTenant(String tenantId, Optional<String> resourceVersion, Span span);
}
