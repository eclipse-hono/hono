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
package org.eclipse.hono.deviceregistry.service.credentials;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Dummy credentials service.
 */
@Service
@Qualifier("backend")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "dummy")
public final class DummyCredentialsService implements CredentialsService {

    private static final String PWD_HASH = getBase64EncodedSha256HashForPassword("hono-secret");

    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, null, span, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Span span, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        final JsonObject result = JsonObject.mapFrom(CredentialsObject.fromHashedPassword(
                authId,
                authId,
                PWD_HASH,
                CredentialsConstants.HASH_FUNCTION_SHA256,
                null, null,
                null));
        resultHandler.handle(Future.succeededFuture(
                CredentialsResult.from(HttpURLConnection.HTTP_OK, JsonObject.mapFrom(result), CacheDirective.noCacheDirective())));
    }

    private static String getBase64EncodedSha256HashForPassword(final String password) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(CredentialsConstants.HASH_FUNCTION_SHA256);
            return Base64.getEncoder().encodeToString(digest.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            return null;
        }
    }
}
