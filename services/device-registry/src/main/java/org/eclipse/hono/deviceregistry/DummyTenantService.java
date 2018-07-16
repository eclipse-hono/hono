/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.service.tenant.BaseTenantService;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;

/**
 *
 */
@Service
@ConditionalOnProperty(name = "hono.app.type", havingValue = "dummy")
public class DummyTenantService extends BaseTenantService<Object> {

    @Override
    public void setConfig(final Object configuration) {
    }

    @Override
    public void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        final TenantObject tenant = new TenantObject();
        tenant.setTenantId(tenantId);
        tenant.setEnabled(true);
        resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_OK,
                JsonObject.mapFrom(tenant),
                null)));
    }
}
