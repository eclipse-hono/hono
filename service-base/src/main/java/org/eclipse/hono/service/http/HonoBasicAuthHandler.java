/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.http;

import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpanContext;
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
 */
public class HonoBasicAuthHandler extends BasicAuthHandlerImpl {

    private final Tracer tracer;

    /**
     * Creates a new handler for an auth provider and a realm name.
     * 
     * @param authProvider The provider to use for validating credentials.
     * @param realm The realm name.
     * @param tracer The tracer to use.
     */
    public HonoBasicAuthHandler(final AuthProvider authProvider, final String realm, final Tracer tracer) {
        super(authProvider, realm);
        this.tracer = tracer;
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
        final RoutingContextDecorator routingContextDecorator = new RoutingContextDecorator(context.currentRoute(),
                context) {

            @Override
            public void fail(final Throwable throwable) {
                HttpUtils.badRequest(context, "Malformed authorization header");
            }
        };
        super.parseCredentials(routingContextDecorator, ar -> {
            if (ar.succeeded()) {
                final SpanContext spanContext = TracingHandler.serverSpanContext(context);
                if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
                    TracingHelper.injectSpanContext(tracer, spanContext, ar.result());
                }
            }
            handler.handle(ar);
        });
    }
}
