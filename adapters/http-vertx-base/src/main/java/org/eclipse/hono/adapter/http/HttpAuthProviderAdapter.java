/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.ExecutionContextAuthHandler;
import org.eclipse.hono.service.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.service.http.HttpContext;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

/**
 * A vert.x {@link io.vertx.ext.auth.AuthProvider} implementation that wraps a {@link DeviceCredentialsAuthProvider}
 * so that it can be used from a plain vert.x {@link io.vertx.ext.web.handler.AuthHandler}, more specifically from
 * an implementation based on the {@link io.vertx.ext.web.handler.impl.AuthHandlerImpl} class.
 * <p>
 * Usually, a {@link DeviceCredentialsAuthProvider} is used from a Hono {@link org.eclipse.hono.service.auth.device.AuthHandler}
 * that will invoke the Hono specific AuthProvider methods. This makes sure that an <em>OpenTracing</em> span context
 * can be transferred directly (without serialization/deserialization) to the AuthProvider. It also allows a
 * <em>PreCredentialsValidationHandler</em> to be invoked with credentials obtained via the AuthProvider.
 * <p>
 * With this adapter class, both these features are available when using a vert.x {@link io.vertx.ext.web.handler.impl.AuthHandlerImpl}
 * implementation.
 * <p>
 * This is achieved by letting the <em>HttpAuthProviderAdapter</em> be created per <em>HttpContext</em> instance and
 * putting it in the corresponding routing context from where the {@link io.vertx.ext.web.handler.impl.AuthHandlerImpl}
 * implementation will use it during authentication.
 */
public class HttpAuthProviderAdapter implements AuthProvider {

    /**
     * This is {@link io.vertx.ext.web.handler.impl.AuthHandlerImpl#AUTH_PROVIDER_CONTEXT_KEY}.
     */
    static final String AUTH_PROVIDER_CONTEXT_KEY = "io.vertx.ext.web.handler.AuthHandler.provider";

    private final DeviceCredentialsAuthProvider<?> wrappedProvider;
    private final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler;
    private final HttpContext httpContext;

    private HttpAuthProviderAdapter(
            final DeviceCredentialsAuthProvider<?> wrappedProvider,
            final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler,
            final HttpContext httpContext) {
        this.wrappedProvider = wrappedProvider;
        this.preCredentialsValidationHandler = preCredentialsValidationHandler;
        this.httpContext = httpContext;
    }

    /**
     * Wraps the given auth provider and puts it in the given routing context so that
     * the {@link io.vertx.ext.web.handler.impl.AuthHandlerImpl} class will use the resulting
     * <em>HttpAuthProviderAdapter</em> when authenticating the request of the given routing context.
     *
     * @param provider The AuthProvider to wrap/adapt.
     * @param routingContext The routing context in which to put the adapted AuthProvider.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the AuthProvider {@link #authenticate(JsonObject, Handler)} result handler.
     * @throws NullPointerException if provider or routingContext is {@code null}.
     */
    public static void putAdaptedProviderInContext(
            final DeviceCredentialsAuthProvider<?> provider,
            final RoutingContext routingContext,
            final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler) {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(routingContext);

        routingContext.put(AUTH_PROVIDER_CONTEXT_KEY, new HttpAuthProviderAdapter(
                provider,
                preCredentialsValidationHandler,
                HttpContext.from(routingContext)));
    }

    @Override
    public void authenticate(final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {

        // let the "authenticate" method of the wrappedProvider be called by means of an ExecutionContextAuthHandler;
        // this ensures that the preCredentialsValidationHandler is invoked after obtaining credentials
        // and that the tracing context of the httpContext is passed along
        final ExecutionContextAuthHandler<HttpContext> authHandler = new ExecutionContextAuthHandler<>(
                wrappedProvider, preCredentialsValidationHandler) {

            @Override
            public Future<JsonObject> parseCredentials(final HttpContext context) {
                return Future.succeededFuture(authInfo);
            }
        };
        authHandler.authenticateDevice(httpContext).onComplete(ar -> {
            if (ar.succeeded()) {
                resultHandler.handle(Future.succeededFuture(ar.result()));
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

}
