/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.device;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.registration.AbstractRegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Dummy implementation of the registration service.
 */
@Service
@Qualifier("backend")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "dummy")
public class DummyRegistrationService extends AbstractRegistrationService {

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        assertRegistration(tenantId, deviceId, null, span, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        final JsonObject deviceData = new JsonObject();
        getAssertionPayload(tenantId, deviceId, deviceData)
                .compose(payload -> Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, payload)))
                .recover(thr -> Future.succeededFuture(RegistrationResult.from(ServiceInvocationException.extractStatusCode(thr))))
                .setHandler(resultHandler);
    }

    @Override
    protected void getDevice(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.failedFuture("Not implemented"));
    }

    @Override
    protected void resolveGroupMembers(final String tenantId, final JsonArray viaGroups, final Span span,
            final Handler<AsyncResult<JsonArray>> resultHandler) {
        resultHandler.handle(Future.failedFuture("Not implemented"));
    }

}
