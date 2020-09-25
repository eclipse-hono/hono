/**
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.http;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.PreCredentialsValidationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.impl.ChainAuthHandlerImpl;

/**
 * A Hono specific version of vert.x web's standard {@code ChainAuthHandlerImpl}
 * that extracts and handles a {@link ServiceInvocationException} conveyed as the
 * root cause in an {@code HttpStatusException} when an authentication failure
 * occurs.
 * <p>
 * Apart from that, support for a {@link PreCredentialsValidationHandler} and for
 * transferring a span context to the AuthProvider is added here.
 *
 */
public class HonoChainAuthHandler extends ChainAuthHandlerImpl implements HonoHttpAuthHandler {

    private static final Logger LOG = LoggerFactory.getLogger(HonoChainAuthHandler.class);

    private final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler;

    /**
     * Creates a new HonoChainAuthHandler.
     */
    public HonoChainAuthHandler() {
        this(null);
    }

    /**
     * Creates a new HonoChainAuthHandler.
     *
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     *            NOTE: if not {@code null}, the added AuthHandlers must use a {@link DeviceCredentialsAuthProvider}.
     */
    public HonoChainAuthHandler(final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler) {
        this.preCredentialsValidationHandler = preCredentialsValidationHandler;
    }

    /**
     * Appends an auth provider to the chain.
     *
     * @param handler The handler to append.
     * @return This handler for command chaining.
     * @throws IllegalArgumentException if the given handler contains a <em>PreCredentialsValidationHandler</em>
     *             (differing from the one set in this object) which wouldn't be invoked here. In such a scenario the
     *             <em>PreCredentialsValidationHandler</em> should be set on this HonoChainAuthHandler instead (or as
     *             well).
     */
    @Override
    public ChainAuthHandler append(final AuthHandler handler) {
        if (handler instanceof HonoHttpAuthHandler) {
            final PreCredentialsValidationHandler<HttpContext> nestedPreCredValidationHandler = ((HonoHttpAuthHandler) handler)
                    .getPreCredentialsValidationHandler();
            if (nestedPreCredValidationHandler != null && !nestedPreCredValidationHandler.equals(preCredentialsValidationHandler)) {
                LOG.error("{} has PreCredentialsValidationHandler set - not supported in ChainAuthHandler", handler);
                throw new IllegalArgumentException("handler with a PreCredentialsValidationHandler not supported here");
            }
        }
        return super.append(handler);
    }

    @Override
    public PreCredentialsValidationHandler<HttpContext> getPreCredentialsValidationHandler() {
        return preCredentialsValidationHandler;
    }

    @Override
    public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
        context.put(SKIP_POSTPROCESSING_CONTEXT_KEY, Boolean.TRUE); // post-processing only needs to be done here, not in nested AuthHandlers
        super.parseCredentials(context, ar -> {
            if (ar.succeeded()) {
                context.remove(SKIP_POSTPROCESSING_CONTEXT_KEY);
                final AuthProvider authProvider = context.get(HttpAuthProviderAdapter.AUTH_PROVIDER_CONTEXT_KEY);
                processParseCredentialsResult(authProvider, context, null, ar, handler);
            } else {
                handler.handle(ar);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * Delegates error handling to {@link AuthHandlerTools#processException(RoutingContext, Throwable, String)}
     * in order to handle server errors properly.
     */
    @Override
    protected void processException(final RoutingContext ctx, final Throwable exception) {

        if (!ctx.response().ended()) {
            AuthHandlerTools.processException(ctx, exception, authenticateHeader(ctx));
        }
    }
}
