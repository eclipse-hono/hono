/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.device;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.function.Supplier;

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
 * Provides methods to determine the tenant and authentication credentials of a request made to a protocol adapter.
 */
public class CredentialsWithTenantDetailsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialsWithTenantDetailsProvider.class);

    /**
     * The protocol adapter's configuration properties.
     */
    protected final ProtocolAdapterProperties config;
    /**
     * The factory for creating Tenant service clients.
     */
    protected final TenantClientFactory tenantClientFactory;

    /**
     * Creates a new CredentialsWithTenantDetailsProvider instance for the given config and tenantClientFactory.
     *
     * @param config The configuration.
     * @param tenantClientFactory The factory to use for creating a Tenant service client.
     * @throws NullPointerException if either of the parameters is {@code null}.
     */
    public CredentialsWithTenantDetailsProvider(final ProtocolAdapterProperties config,
            final TenantClientFactory tenantClientFactory) {
        this.config = Objects.requireNonNull(config);
        this.tenantClientFactory = Objects.requireNonNull(tenantClientFactory);
    }

    /**
     * Gets credentials and tenant details from the X.509 certificate of the given {@link SSLSession}.
     *
     * @param sslSession The SSL session.
     * @param noCertFoundExceptionSupplier Provides the exception with which the future is failed in case no certificate
     *            was found. If {@code null}, the future is failed with a NoStackTraceThrowable in that case.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     */
    public final Future<TenantDetailsDeviceCredentials> getFromClientCertificate(final SSLSession sslSession,
            final Supplier<Exception> noCertFoundExceptionSupplier, final SpanContext spanContext) {
        if (sslSession == null) {
            return fail(noCertFoundExceptionSupplier, "not ssl");
        }
        final X509Certificate deviceCert = getX509Cert(sslSession);
        if (deviceCert == null) {
            LOG.trace("no X.509 cert found");
            return fail(noCertFoundExceptionSupplier, "no X.509 cert found");
        }
        return getFromClientCertificate(deviceCert, spanContext);
    }

    private Future<TenantDetailsDeviceCredentials> fail(final Supplier<Exception> exceptionSupplier,
            final String alternativeFailureString) {
        if (exceptionSupplier != null) {
            return Future.failedFuture(exceptionSupplier.get());
        } else {
            return Future.failedFuture(alternativeFailureString);
        }
    }

    /**
     * Gets credentials and tenant details from the given X.509 certificate.
     *
     * @param certificate The SSL certificate.
     * @param spanContext The OpenTracing context to use for tracking the operation (may be {@code null}).
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if certificate is {@code null}.
     */
    public final Future<TenantDetailsDeviceCredentials> getFromClientCertificate(final X509Certificate certificate,
            final SpanContext spanContext) {
        Objects.requireNonNull(certificate);
        final X500Principal x500Principal = certificate.getIssuerX500Principal();
        final String subjectDnAuthId = certificate.getSubjectX500Principal().getName();
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
     * Gets credentials and tenant details from the given username.
     * <p>
     * If Hono is configured for single tenant mode, the returned tenant is {@link Constants#DEFAULT_TENANT} and
     * <em>authId</em> is set to the given username.
     * <p>
     * If Hono is configured for multi tenant mode, the given username is split in two around the first occurrence of
     * the <code>&#64;</code> sign. <em>authId</em> is then set to the first part and the tenant id is derived from the
     * second part.
     *
     * @param userName The user name in the format <em>authId@tenantId</em>.
     * @param invalidUserNameExceptionSupplier Provides the exception with which the future is failed in case the given
     *            user name is invalid. If {@code null}, the future is failed with a NoStackTraceThrowable in that case.
     * @param spanContext The OpenTracing context to use for tracking the operation.
     * @return A future indicating the outcome of the operation.
     * @see org.eclipse.hono.service.auth.device.UsernamePasswordCredentials
     */
    public final Future<TenantDetailsDeviceCredentials> getFromUserName(final String userName,
            final Supplier<Exception> invalidUserNameExceptionSupplier, final SpanContext spanContext) {
        if (userName == null) {
            return fail(invalidUserNameExceptionSupplier, "user name not set");
        }
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials
                .create(userName, "", config.isSingleTenant());
        if (credentials == null) {
            return fail(invalidUserNameExceptionSupplier, "unsupported user name format");
        }
        return tenantClientFactory.getOrCreateTenantClient()
                .compose(tenantClient -> tenantClient.get(credentials.getTenantId(), spanContext))
                .map(tenantObject -> new TenantDetailsDeviceCredentials(tenantObject, credentials));
    }

    private Future<TenantDetailsDeviceCredentials> get(final X500Principal x500Principal,
            final String subjectDnAuthId, final SpanContext spanContext) {
        return tenantClientFactory.getOrCreateTenantClient()
                .compose(tenantClient -> tenantClient.get(x500Principal, spanContext))
                .map(tenantObject -> new TenantDetailsDeviceCredentials(tenantObject, SubjectDnCredentials.create(
                        tenantObject.getTenantId(), subjectDnAuthId)));
    }

    /**
     * Gets details for the given tenant and wraps them along with the given authentication identifier in the
     * returned credentials.
     *
     * @param tenantId The tenant id.
     * @param authId The authentication identifier or {@code null}.
     * @param credentialsType The type of credentials given.
     * @param spanContext The OpenTracing context to use for tracking the operation.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if tenantId or credentialsType is {@code null}.
     */
    public final Future<TenantDetailsDeviceCredentials> get(final String tenantId, final String authId,
            final String credentialsType, final SpanContext spanContext) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(credentialsType);
        return tenantClientFactory.getOrCreateTenantClient()
                .compose(tenantClient -> tenantClient.get(tenantId, spanContext))
                .map(tenantObject -> new TenantDetailsDeviceCredentials(tenantObject, authId, credentialsType));
    }
}
