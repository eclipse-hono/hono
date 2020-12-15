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
import java.util.Set;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.RegistrationResult;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Dummy implementation of the registration service which successfully
 * asserts the registration status of all devices.
 */
public final class NoopRegistrationService extends AbstractRegistrationService {

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

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<RegistrationResult> getRegistrationInformation(final DeviceKey deviceKey, final Span span) {
        return Future.failedFuture("Not implemented");
    }

    @Override
    protected Future<Set<String>> processResolveGroupMembers(final String tenantId, final Set<String> viaGroups, final Span span) {
        return Future.failedFuture("Not implemented");
    }
}
