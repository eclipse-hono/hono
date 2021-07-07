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

package org.eclipse.hono.service.management.tenant;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
     * @param tenantId The identifier of the tenant to create.
     * @param tenantObj The configuration information to add for the tenant (may be {@code null}).
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *              An implementation should log (error) events on this span and it may set tags and use this span as the
     *              parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/createTenant">
     *         Device Registry Management API - Create Tenant</a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/createTenant">
     *      Device Registry Management API - Create Tenant</a>
     */
    Future<OperationResult<Id>> createTenant(Optional<String> tenantId, Tenant tenantObj, Span span);

    /**
     * Reads tenant configuration information for a tenant identifier.
     *
     * @param tenantId The identifier of the tenant.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/getTenant">
     *         Device Registry Management API - Get Tenant</a>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/getTenant">
     *      Device Registry Management API - Get Tenant</a>
     */
    Future<OperationResult<Tenant>> readTenant(String tenantId, Span span);

    /**
     * Finds tenants with optional filters, paging and sorting options.
     * <p>
     * This search operation is considered as optional since it is not required for the normal functioning of Hono and
     * is more of a convenient operation. Hence here it is declared as a default method which returns
     * {@link HttpURLConnection#HTTP_NOT_IMPLEMENTED}. It is up to the implementors of this interface to offer an
     * implementation of this service or not.
     *
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *                   retrieve the whole result set page by page.
     * @param filters A list of filters. The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options. The sortOptions specify properties to sort the result set by.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status code</em> is set as specified in the
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/searchTenants"> Device
     *         Registry Management API - Search Tenants</a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/searchTenants"> Device Registry
     *      Management API - Search Tenants</a>
     */
    default Future<OperationResult<SearchResult<TenantWithId>>> searchTenants(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(span);

        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    /**
     * Updates configuration information of a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantObj The updated configuration information for the tenant (may be {@code null}).
     * @param resourceVersion The resource version that the tenant instance is required to have.
     *                        If empty, the resource version of the tenant instance on record will be ignored.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *             An implementation should log (error) events on this span and it may set tags and use this span as the
     *             parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the 
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/updateTenant">
     *         Device Registry Management API - Update Tenant</a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/updateTenant">
     *      Device Registry Management API - Update Tenant</a>
     */
    Future<OperationResult<Void>> updateTenant(String tenantId, Tenant tenantObj, Optional<String> resourceVersion,
            Span span);

    /**
     * Removes a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param resourceVersion The resource version that the tenant instance is required to have.
     *                        If empty, the resource version of the tenant instance on record will be ignored.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *              An implementation should log (error) events on this span and it may set tags and use this span as the
     *              parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the 
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/deleteTenant">
     *         Device Registry Management API - Delete Tenant</a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/deleteTenant">
     *      Device Registry Management API - Delete Tenant</a>
     */
    Future<Result<Void>> deleteTenant(String tenantId, Optional<String> resourceVersion, Span span);
}
