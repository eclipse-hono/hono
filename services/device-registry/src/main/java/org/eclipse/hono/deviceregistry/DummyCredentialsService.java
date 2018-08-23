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
import org.eclipse.hono.service.credentials.BaseCredentialsService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;

/**
 *
 */
@Service
@ConditionalOnProperty(name = "hono.app.type", havingValue = "dummy")
public final class DummyCredentialsService extends BaseCredentialsService<Object> {

    @Override
    public void setConfig(final Object configuration) {

    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, null, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        final JsonObject result = JsonObject.mapFrom(CredentialsObject.fromHashedPassword(authId, authId,
                "hono-secret", "sha-256", null, null, null));
        resultHandler.handle(Future.succeededFuture(
                CredentialsResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(result), CacheDirective.noCacheDirective())));
    }

    @Override
    public void getAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, null, null, resultHandler);
    }

}
