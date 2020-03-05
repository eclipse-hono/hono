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
import io.vertx.core.Future;
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
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final Span span) {
        return assertRegistration(tenantId, deviceId, null, span);
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId,
            final String gatewayId, final Span span) {
        final JsonObject deviceData = new JsonObject();
        return getAssertionPayload(tenantId, deviceId, deviceData)
                .compose(payload -> Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, payload)))
                .recover(thr -> Future
                        .succeededFuture(RegistrationResult.from(ServiceInvocationException.extractStatusCode(thr))));
    }

    @Override
    protected Future<RegistrationResult> getDevice(final String tenantId, final String deviceId, final Span span) {
        return Future.failedFuture("Not implemented");
    }

    @Override
    protected Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups, final Span span) {
        return Future.failedFuture("Not implemented");
    }

}
