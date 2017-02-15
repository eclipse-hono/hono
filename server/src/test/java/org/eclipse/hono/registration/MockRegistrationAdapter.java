/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration;

import java.net.HttpURLConnection;

import org.eclipse.hono.registration.impl.BaseRegistrationService;
import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Simple mock implementation of RegistrationAdapter that always returns success.
 */
public class MockRegistrationAdapter extends BaseRegistrationService {

    @Override
    public void getDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK)));
    }

    @Override
    public void findDevice(String tenantId, String key, String value,
            Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK)));
    }

    @Override
    public void addDevice(String tenantId, String deviceId, JsonObject otherKeys,
            Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_CREATED)));
    }

    @Override
    public void updateDevice(String tenantId, String deviceId, JsonObject otherKeys,
            Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK)));
    }

    @Override
    public void removeDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK)));
    }
}