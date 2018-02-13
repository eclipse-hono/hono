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

package org.eclipse.hono.client;


import org.eclipse.hono.util.TenantResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Tenant API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/api/TODOI">
 * Tenant API specification</a> for a description of the result codes returned.
 * </p>
 */
public interface TenantClient extends RequestResponseClient {

    void add(final JsonObject data, Handler<AsyncResult<TenantResult>> resultHandler);

    void get(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler);

    void update(String tenantId, final JsonObject data, Handler<AsyncResult<TenantResult>> resultHandler);
}