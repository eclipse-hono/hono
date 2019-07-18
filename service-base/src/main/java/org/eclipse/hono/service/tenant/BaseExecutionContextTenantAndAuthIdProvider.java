/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.tenant;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A base class for {@link ExecutionContextTenantAndAuthIdProvider} implementations.
 */
public class BaseExecutionContextTenantAndAuthIdProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BaseExecutionContextTenantAndAuthIdProvider.class);

    protected final ProtocolAdapterProperties config;
    protected final TenantClientFactory tenantClientFactory;

    /**
     * Creates a new BaseExecutionContextTenantAndAuthIdProvider for the given config and tenantClientFactory.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public BaseExecutionContextTenantAndAuthIdProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        this.config = Objects.requireNonNull(config);
        this.tenantClientFactory = Objects.requireNonNull(tenantClientFactory);
    }

    /**
     * Gets a {@link TenantObjectWithAuthId} from the X509 certificate of the given {@link SSLSession}.
     *
     * @param sslSession The SSL session.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if sslSession is {@code null}.
     */
    protected Future<TenantObjectWithAuthId> getFromClientCertificate(final SSLSession sslSession,
            final SpanContext spanContext) {
        Objects.requireNonNull(sslSession);
        final X509Certificate deviceCert = getX509Cert(sslSession);
        if (deviceCert == null) {
            return Future.failedFuture("no cert found");
        }
        final X500Principal x500Principal = deviceCert.getIssuerX500Principal();
        final String subjectDnAuthId = deviceCert.getSubjectX500Principal().getName();
        return get(x500Principal, subjectDnAuthId, spanContext);
    }

    private X509Certificate getX509Cert(final SSLSession sslSession) {
        try {
            final Certificate[] path = sslSession.getPeerCertificates();
            if (path.length > 0 && path[0] instanceof X509Certificate) {
                return (X509Certificate) path[0];
            }
        } catch (final SSLPeerUnverifiedException e) {
            LOG.debug("certificate chain cannot be read: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Gets a {@link TenantObjectWithAuthId} from the given username.
     * <p>
     * If Hono is configured for single tenant mode, the returned tenant is {@link Constants#DEFAULT_TENANT} and
     * <em>authId</em> is set to the given username.
     * <p>
     * If Hono is configured for multi tenant mode, the given username is split in two around the first occurrence of
     * the <code>&#64;</code> sign. <em>authId</em> is then set to the first part and the tenant id is derived from the
     * second part.
     *
     * @param userName The user name.
     * @param spanContext The OpenTracing context to use for tracking the operation.
     * @return A future indicating the outcome of the operation.
     */
    protected Future<TenantObjectWithAuthId> getFromUserName(final String userName, final SpanContext spanContext) {
        if (userName == null) {
            return Future.failedFuture("user name not set");
        }
        final String tenantId;
        final String authId;
        if (config.isSingleTenant()) {
            tenantId = Constants.DEFAULT_TENANT;
            authId = userName;
        } else {
            // userName is <authId>@<tenantId>
            final String[] userComponents = userName.split("@", 2);
            if (userComponents.length == 2) {
                tenantId = userComponents[1];
                authId = userComponents[0];
            } else {
                tenantId = null;
                authId = null;
            }
        }
        if (tenantId == null) {
            return Future.failedFuture("unsupported user name format");
        }
        return get(tenantId, authId, spanContext);
    }

    private Future<TenantObjectWithAuthId> get(final X500Principal x500Principal,
            final String subjectDnAuthId, final SpanContext spanContext) {
        return tenantClientFactory.getOrCreateTenantClient()
                .compose(tenantClient -> tenantClient.get(x500Principal, spanContext))
                .map(tenantObject -> new TenantObjectWithAuthId(tenantObject, subjectDnAuthId));
    }

    private Future<TenantObjectWithAuthId> get(final String tenantId, final String authId,
            final SpanContext spanContext) {
        return tenantClientFactory.getOrCreateTenantClient()
                .compose(tenantClient -> tenantClient.get(tenantId, spanContext))
                .map(tenantObject -> new TenantObjectWithAuthId(tenantObject, authId));
    }

}
