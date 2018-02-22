/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import java.net.HttpURLConnection;

import org.eclipse.hono.util.TenantResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * A service for keeping record of tenant information.
 *
 */
public interface TenantService extends Verticle {

    /**
     * Gets tenant data by tenant ID.
     *
     * @param tenantId The ID of he tenant for which data is requested.
     * @param resultHandler The handler to invoke with the result of the operation. If tenant exists, the
     *            <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain the
     *            tenant object. Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void get(String tenantId, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);

    /**
     * Creates a new Tenant.
     *
     * @param tenantId The ID of the tenant that shall be created.
     * @param tenantObj A map containing the properties of the tenant (may be {@code null}).
     * @param resultHandler The handler to invoke with the result of the operation. If a tenant with the given ID does
     *            not yet exist, the <em>status</em> will be {@link HttpURLConnection#HTTP_CREATED}.
     *            Otherwise the status will be {@link HttpURLConnection#HTTP_CONFLICT}.
     */
    void add(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);

    /**
     * Updates tenant data.
     *
     * @param tenantId The tenant ID.
     * @param tenantObj A map containing the properties of the tenant (may be {@code null}).
     * @param resultHandler The handler to invoke with the result of the operation. If a tenant with the given ID exists
     *         and was updated, the <em>status</em> will be {@link HttpURLConnection#HTTP_NO_CONTENT}.
               Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void update(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);

    /**
     * Removes a tenant.
     *
     * @param tenantId The tenant ID.
     * @param resultHandler The handler to invoke with the result of the operation. If the tenant has been removed, the
     *            <em>status</em> will be {@link HttpURLConnection#HTTP_NO_CONTENT}. Otherwise the status will be
     *            {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    void remove(String tenantId, Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler);
}
