/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.tenant;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.TenantResult;
import java.net.HttpURLConnection;

/**
 * A service for keeping record of tenant information.
 *
 */
public interface TenantService extends Verticle {

    /**
     * Gets tenant data by tenant ID.
     *
     * @param tenantId The tenant ID.
     * @param resultHandler The handler to invoke with the result of the operation. If tenant exists, the
     *            <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain the
     *            tenant object. Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void get(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler);

    /**
     * Creates a Tenant
     *
     * @param tenantObj A map containing the tenant object.
     * @param resultHandler The handler to invoke with the result of the operation. If a device with the given ID does
     *            not yet exist for the tenant, the <em>status</em> will be {@link HttpURLConnection#HTTP_CREATED}.
     *            Otherwise the status will be {@link HttpURLConnection#HTTP_CONFLICT}.
     */
    void add(JsonObject tenantObj, Handler<AsyncResult<TenantResult>> resultHandler);

    /**
     * Updates device registration data.
     *
     * @param tenantId The tenant ID.
     * @param tenantObj A map containing the tenant object.
     * @param resultHandler The handler to invoke with the result of the operation. If a tenant with the given ID exists
     *            the <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain
     *            the tenant object that had originally been created. Otherwise the status will be
     *            {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void update(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult>> resultHandler);

    // TODO discuss what should happen if we delete a tenant that has devices or credentials registered to it
    /**
     * Removes a tenant.
     *
     * @param tenantId The tenant ID.
     * @param resultHandler The handler to invoke with the result of the operation. If the tenant has been removed, the
     *            <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain the
     *            tenant object that has been removed. Otherwise the status will be
     *            {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void remove(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler);
}
