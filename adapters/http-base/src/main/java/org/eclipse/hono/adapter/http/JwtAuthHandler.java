/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.http;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.adapter.auth.device.jwt.CredentialsParser;
import org.eclipse.hono.adapter.auth.device.jwt.DefaultJwsValidator;
import org.eclipse.hono.adapter.auth.device.jwt.JwtCredentials;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.handler.impl.HTTPAuthorizationHandler;

/**
 * An auth handler for extracting an {@value CredentialsConstants#FIELD_AUTH_ID},
 * {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and JSON Web Token (JWT) from an HTTP context.
 */
public final class JwtAuthHandler extends HTTPAuthorizationHandler<AuthenticationProvider> implements CredentialsParser {

    private final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler;

    /**
     * Creates a new handler for an authentication provider and a Tenant service client.
     *
     * @param authProvider The authentication provider to use for verifying the device identity.
     * @param realm The realm name.
     */
    public JwtAuthHandler(final DeviceCredentialsAuthProvider<JwtCredentials> authProvider, final String realm) {
        this(authProvider, realm, null);
    }

    /**
     * Creates a new handler for an authentication provider and a Tenant service client.
     *
     * @param authProvider The authentication provider to use for verifying the device identity.
     * @param realm The realm name.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     */
    public JwtAuthHandler(final DeviceCredentialsAuthProvider<JwtCredentials> authProvider,
            final String realm,
            final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler) {
        super(authProvider, Type.BEARER, realm);
        this.preCredentialsValidationHandler = preCredentialsValidationHandler;
    }

    @Override
    public void authenticate(final RoutingContext context, final Handler<AsyncResult<User>> handler) {

        parseAuthorization(context, parseAuthorization -> {
            if (parseAuthorization.failed()) {
                handler.handle(Future.failedFuture(parseAuthorization.cause()));
                return;
            }

            final String token = parseAuthorization.result();
            try {
                final var claims = DefaultJwsValidator.getJwtClaims(token);
                final JsonObject credentials;
                if (Objects.equals(claims.getString(Claims.AUDIENCE), CredentialsConstants.AUDIENCE_HONO_ADAPTER)) {
                    // extract tenant, device ID and issuer from claims
                    credentials = parseCredentialsFromClaims(claims);
                } else {
                    // extract tenant and device ID from MQTT client identifier
                    credentials = parseCredentialsFromString(context.request().uri());
                }
                credentials.put(CredentialsConstants.FIELD_PASSWORD, token);

                final ExecutionContextAuthHandler<HttpContext> authHandler = new ExecutionContextAuthHandler<>(
                        (DeviceCredentialsAuthProvider<?>) authProvider,
                        preCredentialsValidationHandler) {

                    @Override
                    public Future<JsonObject> parseCredentials(final HttpContext context) {
                        return Future.succeededFuture(credentials);
                    }
                };

                authHandler.authenticateDevice(HttpContext.from(context))
                        .map(User.class::cast)
                        .onComplete(handler);

            } catch (final MalformedJwtException e) {
                handler.handle(Future.failedFuture(new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, "Malformed token")));
            } catch (final ServiceInvocationException e) {
                handler.handle(Future.failedFuture(new HttpException(HttpURLConnection.HTTP_BAD_REQUEST, e)));
            }
        });
    }

    /**
     * Fails the context with the error code determined from an exception.
     * <p>
     * This method invokes {@link AuthHandlerTools#processException(RoutingContext, Throwable, String)}.
     *
     * @param ctx The routing context.
     * @param exception The cause of failure to process the request.
     */
    @Override
    protected void processException(final RoutingContext ctx, final Throwable exception) {

        if (ctx.response().ended()) {
            return;
        }

        AuthHandlerTools.processException(ctx, exception, null);
    }

    /**
     * Extracts the tenant-id and auth-id from a URI.
     *
     * @param uri A URI containing the tenant-id and auth-id.
     * @return A JsonObject containing values for "tenant-id", "auth-id" and "iss" (same as "auth-id") extracted from the URI.
     * @throws NullPointerException if the given string is {@code null}.
     * @throws ClientErrorException If tenant-id or auth-id cannot correctly be extracted from the URI.
     */
    @Override
    public JsonObject parseCredentialsFromString(final String uri) {

        Objects.requireNonNull(uri);

        final String[] uriSplit = uri.split("/");

        if (uriSplit.length < 4) {
            throw new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "URI must contain tenant and device ID");
        }
        final var tenant = uriSplit[2];
        final var authId = uriSplit[3];
        return new JsonObject()
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, tenant)
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(Claims.ISSUER, authId);
    }
}
