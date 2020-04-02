/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * This is an implementation of the credentials service and the credentials management service where data is 
 * stored in a mongodb database.
 * 
 * @see <a href="https://www.eclipse.org/hono/docs/api/credentials/">Credentials API</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
@Component
@Qualifier("serviceImpl")
public class MongoDbBasedCredentialsService extends AbstractVerticle
        implements CredentialsManagementService, CredentialsService {

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type,
            final String authId, final Span span) {
        // TODO
        return null;
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type,
            final String authId,
            final JsonObject clientContext, final Span span) {
        // TODO
        return null;
    }

    @Override
    public Future<OperationResult<Void>> updateCredentials(final String tenantId, final String deviceId,
            final List<CommonCredential> credentials, final Optional<String> resourceVersion, final Span span) {
        // TODO
        return Future.succeededFuture(
                OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, null, Optional.empty(), Optional.empty()));
    }

    @Override
    public Future<OperationResult<List<CommonCredential>>> readCredentials(final String tenantId,
            final String deviceId,
            final Span span) {
        // TODO
        return null;
    }

    /**
     * Remove all the credentials for the given device ID.
     *
     * @param tenantId the Id of the tenant which the device belongs to.
     * @param deviceId the id of the device that is deleted.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be <em>204 No Content</em> 
     *         if the operation completed successfully.
     * @throws NullPointerException if any of the parameters except span is {@code null}.
     */
    public Future<Result<Void>> removeCredentials(final String tenantId, final String deviceId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        // TODO
        return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT));
    }
}
