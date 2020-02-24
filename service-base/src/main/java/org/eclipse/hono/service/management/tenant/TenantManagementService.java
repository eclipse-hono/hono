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

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.Optional;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;

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
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>201 Created</em> if the tenant has been added successfully.</li>
     *             <li><em>409 Conflict</em> if a tenant with the given identifier and version already exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/createTenant">
     *      Device Registry Management API - Create Tenant</a>
     */
    void createTenant(Optional<String> tenantId, Tenant tenantObj, Span span, Handler<AsyncResult<OperationResult<Id>>> resultHandler);

    /**
     * Reads tenant configuration information for a tenant identifier.
     *
     * @param tenantId The identifier of the tenant.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em> if a tenant with the given ID is registered. The <em>payload</em> will contain the
     *            tenant's configuration information.</li>
     *            <li><em>404 Not Found</em> if no tenant with the given identifier and version exists.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/getTenant">
     *      Device Registry Management API - Get Tenant</a>
     */
    void readTenant(String tenantId, Span span, Handler<AsyncResult<OperationResult<Tenant>>> resultHandler);

    /**
     * Updates configuration information of a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantObj The updated configuration information for the tenant (may be {@code null}).
     * @param resourceVersion The identifier of the resource version to update.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *             An implementation should log (error) events on this span and it may set tags and use this span as the
     *             parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the tenant has been updated successfully.</li>
     *             <li><em>404 Not Found</em> if no tenant with the given identifier and version exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/updateTenant">
     *      Device Registry Management API - Update Tenant</a>
     */
    void updateTenant(String tenantId, Tenant tenantObj, Optional<String> resourceVersion,
            Span span, Handler<AsyncResult<OperationResult<Void>>> resultHandler);

    /**
     * Removes a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param resourceVersion The identifier of the resource version to delete.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *              An implementation should log (error) events on this span and it may set tags and use this span as the
     *              parent for any spans created in this method.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the tenant has been removed successfully.</li>
     *             <li><em>404 Not Found</em> if no tenant with the given identifier and version exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/deleteTenant">
     *      Device Registry Management API - Delete Tenant</a>
     */
    void deleteTenant(String tenantId, Optional<String> resourceVersion, Span span, Handler<AsyncResult<Result<Void>>> resultHandler);
}
