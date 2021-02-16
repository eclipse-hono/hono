/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpUtils;

import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl;
import io.vertx.ext.web.impl.RoutingContextDecorator;


/**
 * A Hono specific version of vert.x web's standard {@code BasicAuthHandlerImpl}
 * that extracts and handles a {@link org.eclipse.hono.client.ServiceInvocationException} conveyed as the
 * root cause in an {@code HttpStatusException} when an authentication failure
 * occurs.
 * <p>
 * Apart from that, support for a {@link PreCredentialsValidationHandler} and for
 * transferring a span context to the AuthProvider is added here.
 *
 */
public class HonoBasicAuthHandler extends BasicAuthHandlerImpl implements HonoHttpAuthHandler {

    private final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler;
    private final Tracer tracer;

    /**
     * Creates a new handler for an auth provider and a realm name.
     * <p>
     * This constructor is intended to be used with an auth provider that doesn't extend
     * {@link DeviceCredentialsAuthProvider}.
     *
     * @param authProvider The provider to use for validating credentials.
     * @param realm The realm name.
     * @param tracer The tracer to use.
     * @throws NullPointerException If authProvider is {@code null}.
     */
    public HonoBasicAuthHandler(final AuthProvider authProvider, final String realm, final Tracer tracer) {
        super(Objects.requireNonNull(authProvider), realm);
        this.tracer = tracer;
        this.preCredentialsValidationHandler = null;
    }

    /**
     * Creates a new handler for an auth provider and a realm name.
     *
     * @param authProvider The provider to use for validating credentials.
     * @param realm The realm name.
     * @throws NullPointerException If authProvider is {@code null}.
     */
    public HonoBasicAuthHandler(final DeviceCredentialsAuthProvider<?> authProvider, final String realm) {
        this(authProvider, realm, (PreCredentialsValidationHandler<HttpContext>) null);
    }

    /**
     * Creates a new handler for an auth provider and a realm name.
     *
     * @param authProvider The provider to use for validating credentials.
     * @param realm The realm name.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     * @throws NullPointerException If authProvider is {@code null}.
     */
    public HonoBasicAuthHandler(
            final DeviceCredentialsAuthProvider<?> authProvider,
            final String realm,
            final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler) {
        super(Objects.requireNonNull(authProvider), realm);
        this.tracer = null;
        this.preCredentialsValidationHandler = preCredentialsValidationHandler;
    }

    @Override
    public PreCredentialsValidationHandler<HttpContext> getPreCredentialsValidationHandler() {
        return preCredentialsValidationHandler;
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

        AuthHandlerTools.processException(ctx, exception, authenticateHeader(ctx));
    }

    @Override
    public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
        // In case of exception due to malformed authorisation header, Vertx BasicAuthHandlerImpl invokes
        // context.fail(e) thereby the DefaultFailureHandler is being invoked. This sends http status code 500
        // instead of 400. To resolve this, the RoutingContextDecorator is used. The overridden fail(Throwable) method
        // ensures that http status code 400 is returned.
        final RoutingContextDecorator routingContextDecorator = new RoutingContextDecorator(context.currentRoute(), context) {

            @Override
            public void fail(final Throwable throwable) {
                HttpUtils.badRequest(context, "Malformed authorization header");
            }
        };
        super.parseCredentials(routingContextDecorator,
                ar -> processParseCredentialsResult(authProvider, context, tracer, ar, handler));
    }
}
