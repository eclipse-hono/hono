/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.auth.device.jwt;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.adapter.auth.device.AuthHandler;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.ExternalJwtAuthTokenValidator;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;

import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * An authentication provider that verifies JSON Web Token (JWT) credentials using Hono's <em>Credentials</em> API.
 */
public class JwtAuthProvider extends CredentialsApiAuthProvider<JwtCredentials> {

    final ExternalJwtAuthTokenValidator externalJwtAuthTokenValidator;

    /**
     * Creates a new authentication provider for a credentials client.
     *
     * @param credentialsClient The client for accessing the Credentials service.
     * @param tracer The tracer instance.
     * @throws NullPointerException if the client or the tracer are {@code null}.
     */
    public JwtAuthProvider(final CredentialsClient credentialsClient,
            final Tracer tracer) {
        this(credentialsClient, tracer, new ExternalJwtAuthTokenValidator());
    }

    /**
     * Creates a new authentication provider for a credentials client.
     *
     * @param credentialsClient The client for accessing the Credentials service.
     * @param tracer The tracer instance.
     * @param externalJwtAuthTokenValidator The AuthTokenValidator instance.
     * @throws NullPointerException if the client or the tracer are {@code null}.
     */
    public JwtAuthProvider(final CredentialsClient credentialsClient,
            final Tracer tracer, final ExternalJwtAuthTokenValidator externalJwtAuthTokenValidator) {
        super(credentialsClient, tracer);
        this.externalJwtAuthTokenValidator = externalJwtAuthTokenValidator;
    }

    /**
     * Creates a {@link JwtCredentials} instance from auth info provided by a device.
     * <p>
     * The JSON object passed in is required to contain a <em>tenantId</em>, <em>clientId</em> and a <em>password</em>
     * property.
     *
     * @param authInfo The credentials provided by the device. These usually get assembled via
     *            {@link AuthHandler#parseCredentials(org.eclipse.hono.util.ExecutionContext)}.
     * @return The {@link JwtCredentials} instance created from the auth info or {@code null} if the auth info does not
     *         contain the required information.
     * @throws NullPointerException if the auth info is {@code null}.
     */
    @Override
    public JwtCredentials getCredentials(final JsonObject authInfo) {

        Objects.requireNonNull(authInfo);
        try {
            final String tenantId = authInfo.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID);
            final String authId = authInfo.getString(CredentialsConstants.FIELD_AUTH_ID);
            final String jwt = authInfo.getString(CredentialsConstants.FIELD_PASSWORD);
            if (tenantId == null || authId == null || jwt == null) {
                return null;
            } else {
                final JsonObject clientContext = authInfo.copy();
                // credentials object already contains tenant ID, client ID and the JWT from the password field, so
                // remove them from the client context
                clientContext.remove(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID);
                clientContext.remove(CredentialsConstants.FIELD_AUTH_ID);
                clientContext.remove(CredentialsConstants.FIELD_PASSWORD);
                return JwtCredentials.create(tenantId, authId, jwt, clientContext);
            }
        } catch (final ClassCastException | IllegalArgumentException e) {
            log.warn("Reading authInfo failed", e);
            return null;
        }
    }

    @Override
    protected Future<DeviceUser> doValidateCredentials(final JwtCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {
        final Context currentContext = Vertx.currentContext();
        if (currentContext == null) {
            return Future.failedFuture(new IllegalStateException("not running on vert.x Context"));
        }
        final Promise<DeviceUser> result = Promise.promise();
        currentContext.executeBlocking(blockingCodeHandler -> {
            log.debug("validating JWT on vert.x worker thread [{}]", Thread.currentThread().getName());
            if (checkJwtValidity(deviceCredentials, credentialsOnRecord)) {
                blockingCodeHandler
                        .complete(new DeviceUser(deviceCredentials.getTenantId(), credentialsOnRecord.getDeviceId()));
            } else {
                blockingCodeHandler
                        .fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
            }
        }, false, result);
        return result.future();
    }

    private boolean checkJwtValidity(final JwtCredentials deviceCredentials,
            final CredentialsObject credentialsOnRecord) {
        try {
            externalJwtAuthTokenValidator.setCredentialsObject(credentialsOnRecord);
            externalJwtAuthTokenValidator.expand(deviceCredentials.getJwt());
            return true;
        } catch (RuntimeException e) {
            log.debug("JWT validity check failed", e);
            return false;
        }
    }
}
