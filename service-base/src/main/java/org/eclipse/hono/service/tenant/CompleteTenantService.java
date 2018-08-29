/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.tenant;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.TenantResult;

/**
 * A service for keeping record of tenant information.
 * This interface presents all the available operations on the API.
 * See {@link TenantService} for the mandatory only API.
 *
 * @see <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>
 */
public interface CompleteTenantService extends TenantService {

    /**
     * Creates a new Tenant.
     *
     * @param tenantId The identifier of the tenant to add.
     * @param tenantObj The configuration information to add for the tenant (may be {@code null}).
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>201 Created</em> if the tenant has been added successfully.</li>
     *             <li><em>409 Conflict</em> if a tenant with the given identifier already exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/tenant-api/#add-tenant">
     *      Tenant API - Add Tenant</a>
     */
    void add(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);

    /**
     * Updates configuration information of a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param tenantObj The updated configuration information for the tenant (may be {@code null}).
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the tenant has been updated successfully.</li>
     *             <li><em>404 Not Found</em> if no tenant with the given identifier exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/tenant-api/#update-tenant">
     *      Tenant API - Update Tenant</a>
     */
    void update(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);

    /**
     * Removes a tenant.
     *
     * @param tenantId The identifier of the tenant.
     * @param resultHandler The handler to invoke with the result of the operation.
     *             The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the tenant has been removed successfully.</li>
     *             <li><em>404 Not Found</em> if no tenant with the given identifier exists.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/api/tenant-api/#remove-tenant">
     *      Tenant API - Remove Tenant</a>
     */
    void remove(String tenantId, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);
}
