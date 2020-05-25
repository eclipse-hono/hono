/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.service.http;

import java.util.Base64;
import java.util.Objects;

import javax.net.ssl.SSLSession;

import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.BaseExecutionContextTenantAndAuthIdProvider;
import org.eclipse.hono.util.TenantObjectWithAuthId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

/**
 * Provides a method to determine the tenant and auth-id of a HTTP request from the given HttpContext.
 */
public class HttpContextTenantAndAuthIdProvider extends BaseExecutionContextTenantAndAuthIdProvider<HttpContext> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpContextTenantAndAuthIdProvider.class);

    private final String tenantIdContextParamName;
    private final String deviceIdContextParamName;

    /**
     * Creates a new HttpContextTenantAndAuthIdProvider.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HttpContextTenantAndAuthIdProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        super(config, tenantClientFactory);
        this.tenantIdContextParamName = null;
        this.deviceIdContextParamName = null;
    }

    /**
     * Creates a new HttpContextTenantAndAuthIdProvider.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @param tenantIdContextParamName The name of the HttpContext parameter name that provides the tenant id in case of
     *            an unauthenticated request.
     * @param deviceIdContextParamName The name of the HttpContext parameter name that provides the device id in case of
     *            an unauthenticated request. The device id will be used as auth-id for the created
     *            TenantObjectWithAuthId objects.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HttpContextTenantAndAuthIdProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory,
            final String tenantIdContextParamName,
            final String deviceIdContextParamName) {
        super(config, tenantClientFactory);
        this.tenantIdContextParamName = Objects.requireNonNull(tenantIdContextParamName);
        this.deviceIdContextParamName = Objects.requireNonNull(deviceIdContextParamName);
    }

    @Override
    public Future<TenantObjectWithAuthId> get(final HttpContext context, final SpanContext spanContext) {
        if (config.isAuthenticationRequired()) {
            return getFromClientCertificate(context.getRoutingContext(), spanContext)
                    .recover(thr -> getFromAuthHeader(context.getRoutingContext(), spanContext));
        }
        if (tenantIdContextParamName != null && deviceIdContextParamName != null) {
            final String tenantId = context.get(tenantIdContextParamName);
            final String deviceId = context.get(deviceIdContextParamName);
            if (tenantId != null && deviceId != null) {
                // unauthenticated request
                return tenantClientFactory.getOrCreateTenantClient()
                        .compose(tenantClient -> tenantClient.get(tenantId, spanContext))
                        .map(tenantObject -> new TenantObjectWithAuthId(tenantObject, deviceId));
            }
        }
        return Future.failedFuture("tenant could not be determined");
    }

    /**
     * Get the tenant and auth-id from the client certificate of the SSL session in the given routing context request.
     *
     * @param ctx The execution context.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if tenant and auth-id information could not be retrieved from the given request
     *         or if there was an error obtaining the tenant object. In the latter case the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException}.
     *         <p>
     *         Otherwise the future will contain the created <em>TenantObjectWithAuthId</em>.
     */
    protected final Future<TenantObjectWithAuthId> getFromClientCertificate(final RoutingContext ctx,
            final SpanContext spanContext) {
        if (!ctx.request().isSSL()) {
            return Future.failedFuture("no cert found (not SSL/TLS encrypted)");
        }
        final SSLSession sslSession = ctx.request().sslSession();
        return getFromClientCertificate(sslSession, spanContext);
    }

    /**
     * Get the tenant and auth-id from the <em>authorization</em> header of the given routing context request.
     *
     * @param ctx The execution context.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will fail if tenant and auth-id information could not be retrieved from the header
     *         or if there was an error obtaining the tenant object. In the latter case the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException}.
     *         <p>
     *         Otherwise the future will contain the created <em>TenantObjectWithAuthId</em>.
     */
    protected final Future<TenantObjectWithAuthId> getFromAuthHeader(final RoutingContext ctx,
            final SpanContext spanContext) {
        final String authorizationHeader = ctx.request().headers().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null) {
            return Future.failedFuture("no auth header found");
        }
        String userName = null;
        try {
            final int idx = authorizationHeader.indexOf(' ');
            if (idx > 0 && "Basic".equalsIgnoreCase(authorizationHeader.substring(0, idx))) {
                final String authorization = authorizationHeader.substring(idx + 1);
                final String decoded = new String(Base64.getDecoder().decode(authorization));
                final int colonIdx = decoded.indexOf(":");
                userName = colonIdx != -1 ? decoded.substring(0, colonIdx) : decoded;
            }
        } catch (final RuntimeException e) {
            LOG.debug("error parsing auth header: {}", e.getMessage());
        }
        if (userName == null) {
            return Future.failedFuture("unsupported auth header value");
        }
        return getFromUserName(userName, spanContext);
    }
}
