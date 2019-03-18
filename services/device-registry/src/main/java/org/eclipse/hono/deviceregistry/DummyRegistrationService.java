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

import java.net.HttpURLConnection;

import org.eclipse.hono.service.registration.BaseRegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 *
 */
@Service
@ConditionalOnProperty(name = "hono.app.type", havingValue = "dummy")
public class DummyRegistrationService extends BaseRegistrationService<Object> {

    @Override
    public void setConfig(final Object configuration) {

    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, null, span, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
       final JsonObject deviceData = new JsonObject();

        resultHandler.handle(Future.succeededFuture(RegistrationResult.from(
                HttpURLConnection.HTTP_OK,
                getAssertionPayload(tenantId, deviceId, deviceData))));
    }

    @Override
    protected Future<Void> updateDeviceLastVia(final String tenantId, final String deviceId, final String gatewayId,
            final JsonObject deviceData) {
        return Future.succeededFuture();
    }
}
