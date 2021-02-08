/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.http;

import java.util.Objects;

import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;

/**
 * Interface for Hono {@link io.vertx.ext.web.handler.AuthHandler} implementations.
 *
 */
public interface HonoHttpAuthHandler {

    /**
     * Key for the {@link RoutingContext} to indicate that the post-processing done in
     * {@link #processParseCredentialsResult(AuthProvider, RoutingContext, Tracer, AsyncResult, Handler)}
     * should be skipped.
     */
    String SKIP_POSTPROCESSING_CONTEXT_KEY = HonoHttpAuthHandler.class.getSimpleName() + ".skipPostProcessing";

    /**
     * Gets the PreCredentialsValidationHandler.
     *
     * @return The PreCredentialsValidationHandler or {@code null}.
     */
    PreCredentialsValidationHandler<HttpContext> getPreCredentialsValidationHandler();

    /**
     * To be invoked to post-process the result of {@link io.vertx.ext.web.handler.AuthHandler#parseCredentials}.
     * <p>
     * Makes sure the PreCredentialsValidationHandler of this auth handler is applied (if set) and that the span context
     * of the routing context can be transferred to the given auth provider.
     *
     * @param authProvider The auth provider used in the authentication attempt.
     * @param routingContext The routing context .
     * @param tracer The tracer instance or {@code null}. Only needed if the auth provider isn't a
     *            {@link DeviceCredentialsAuthProvider}.
     * @param parseCredentialsResult The parseCredentials result.
     * @param handler The handler that will be invoked with the parseCredentials result or with a failed future in case
     *            the supplied provider is invalid.
     * @throws NullPointerException if any of the parameters except tracer is {@code null}.
     */
    default void processParseCredentialsResult(
            final AuthProvider authProvider,
            final RoutingContext routingContext,
            final Tracer tracer,
            final AsyncResult<JsonObject> parseCredentialsResult,
            final Handler<AsyncResult<JsonObject>> handler) {
        Objects.requireNonNull(authProvider);
        Objects.requireNonNull(routingContext);
        Objects.requireNonNull(parseCredentialsResult);
        Objects.requireNonNull(handler);

        if (parseCredentialsResult.succeeded()) {
            final Boolean skip = routingContext.get(SKIP_POSTPROCESSING_CONTEXT_KEY);
            if (skip != null && skip) {
                handler.handle(parseCredentialsResult);
            } else if (authProvider instanceof DeviceCredentialsAuthProvider<?>) {
                HttpAuthProviderAdapter.putAdaptedProviderInContext((DeviceCredentialsAuthProvider<?>) authProvider,
                        routingContext, getPreCredentialsValidationHandler());
                handler.handle(parseCredentialsResult);

            } else if (getPreCredentialsValidationHandler() != null) {
                handler.handle(Future.failedFuture(new IllegalStateException(
                        "incompatible authProvider used - doesn't support preCredentialsValidationHandler set on the authHandler")));
            } else {
                if (tracer != null) {
                    TracingHelper.injectSpanContext(tracer, TracingHandler.serverSpanContext(routingContext), parseCredentialsResult.result());
                }
                handler.handle(parseCredentialsResult);
            }
        } else {
            handler.handle(parseCredentialsResult);
        }
    }
}
