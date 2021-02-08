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

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.X509Authentication;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.TracingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.AuthHandlerImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;


/**
 * A handler for authenticating HTTP clients using X.509 client certificates.
 * <p>
 * On successful validation of the certificate, the subject DN of the certificate is used
 * to retrieve X.509 credentials for the device in order to determine the device identifier. 
 * <p>
 * Apart from that, support for a {@link PreCredentialsValidationHandler} and for
 * transferring a span context to the AuthProvider is added here.
 *
 */
public class X509AuthHandler extends AuthHandlerImpl implements HonoHttpAuthHandler {

    private static final Logger LOG = LoggerFactory.getLogger(X509AuthHandler.class);
    private static final HttpStatusException UNAUTHORIZED = new HttpStatusException(HttpURLConnection.HTTP_UNAUTHORIZED);

    private final X509Authentication auth;
    private final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler;

    /**
     * Creates a new handler for an authentication provider and a
     * Tenant service client.
     *
     * @param clientAuth The service to use for validating the client's certificate path.
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @throws NullPointerException if client auth is {@code null}.
     */
    public X509AuthHandler(
            final X509Authentication clientAuth,
            final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider) {
        this(clientAuth, authProvider, null);
    }

    /**
     * Creates a new handler for an authentication provider and a
     * Tenant service client.
     *
     * @param clientAuth The service to use for validating the client's certificate path.
     * @param authProvider The authentication provider to use for verifying
     *              the device identity.
     * @param preCredentialsValidationHandler An optional handler to invoke after the credentials got determined and
     *            before they get validated. Can be used to perform checks using the credentials and tenant information
     *            before the potentially expensive credentials validation is done. A failed future returned by the
     *            handler will fail the corresponding authentication attempt.
     * @throws NullPointerException if client auth is {@code null}.
     */
    public X509AuthHandler(
            final X509Authentication clientAuth,
            final DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider,
            final PreCredentialsValidationHandler<HttpContext> preCredentialsValidationHandler) {
        super(authProvider);
        this.auth = Objects.requireNonNull(clientAuth);
        this.preCredentialsValidationHandler = preCredentialsValidationHandler;
    }

    @Override
    public PreCredentialsValidationHandler<HttpContext> getPreCredentialsValidationHandler() {
        return preCredentialsValidationHandler;
    }

    @Override
    public final void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handler);

        if (context.request().isSSL()) {
            try {
                final Certificate[] path = context.request().sslSession().getPeerCertificates();
                auth.validateClientCertificate(path, TracingHandler.serverSpanContext(context))
                        .onComplete(ar -> processParseCredentialsResult(authProvider, context, null, ar, handler));
            } catch (SSLPeerUnverifiedException e) {
                // client certificate has not been validated
                LOG.debug("could not retrieve client certificate from request: {}", e.getMessage());
                handler.handle(Future.failedFuture(UNAUTHORIZED));
            }
        } else {
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
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
}
