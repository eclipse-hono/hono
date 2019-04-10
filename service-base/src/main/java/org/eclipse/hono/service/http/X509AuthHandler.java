/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.SubjectDnCredentials;
import org.eclipse.hono.service.auth.device.X509Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
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
 *
 */
public class X509AuthHandler extends AuthHandlerImpl {

    private static final Logger LOG = LoggerFactory.getLogger(X509AuthHandler.class);
    private static final HttpStatusException UNAUTHORIZED = new HttpStatusException(HttpURLConnection.HTTP_UNAUTHORIZED);

    private final X509Authentication auth;

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
            final HonoClientBasedAuthProvider<SubjectDnCredentials> authProvider) {
        super(authProvider);
        this.auth = Objects.requireNonNull(clientAuth);
    }

    @Override
    public final void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {

        Objects.requireNonNull(context);
        Objects.requireNonNull(handler);

        if (context.request().isSSL()) {
            try {
                final Certificate[] path = context.request().sslSession().getPeerCertificates();
                final SpanContext currentSpan = TracingHandler.serverSpanContext(context);

                auth.validateClientCertificate(path, currentSpan).setHandler(handler);
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
