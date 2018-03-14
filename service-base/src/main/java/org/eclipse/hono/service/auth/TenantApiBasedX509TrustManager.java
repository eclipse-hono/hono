/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.auth;

import java.security.GeneralSecurityException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.net.ssl.X509TrustManager;

import org.eclipse.hono.client.HonoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

/**
 * A trust manager which uses the Tenant API to determine if a device's
 * X.509 certificate is trusted.
 *
 */
public class TenantApiBasedX509TrustManager implements X509TrustManager {

    private static final Logger LOG = LoggerFactory.getLogger(TenantApiBasedX509TrustManager.class);

    private final HonoClient client;

    /**
     * Creates a trust manager for a Tenant service client.
     * 
     * @param client The client.
     */
    public TenantApiBasedX509TrustManager(final HonoClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /**
     * Checks if the client certificate chain is trusted.
     * <p>
     * Retrieves tenant configuration data using the <em>issuer DN</em> from the client's certificate.
     * This method then tries to create a chain of trust using the client certificate and the
     * trusted CA certificate from the tenant configuration.
     * 
     * @param chain The certificate chain provided by the client as part of the TLS handshake.
     * @param authType The cryptographic algorithm the certificate is based on, e.g. <em>RSA</em>.
     */
    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {

        Objects.requireNonNull(chain);

        final X509Certificate deviceCertificate = chain[0];
        final CompletableFuture<Void> tenantRequest = new CompletableFuture<>();

        client.isConnected().compose(ok -> client.getOrCreateTenantClient())
            .compose(tenantClient -> tenantClient.get(deviceCertificate.getIssuerX500Principal()))
            .compose(tenant -> {
                if (!tenant.isEnabled()) {
                    return Future.failedFuture(new GeneralSecurityException("tenant is disabled"));
                } else if (tenant.getTrustAnchor() == null) {
                    return Future.failedFuture(new GeneralSecurityException("no trust anchor configured for tenant"));
                } else {
                    return checkCertPath(deviceCertificate, tenant.getTrustAnchor());
                }
            }).setHandler(validation -> {
                if (validation.succeeded()) {
                    tenantRequest.complete(null);
                } else {
                    tenantRequest.completeExceptionally(validation.cause());
                }
            });

        try {
            tenantRequest.join();
        } catch (CompletionException e) {
            throw new CertificateException("validation of device certificate failed", e.getCause());
        }
    }

    private Future<Void> checkCertPath(final X509Certificate deviceCertificate, final TrustAnchor trustAnchor) {

        final Future<Void> result = Future.future();
        try {
            final PKIXParameters params = new PKIXParameters(Collections.singleton(trustAnchor));
            // TODO do we need to check for revocation?
            params.setRevocationEnabled(false);
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            final CertPath certPath = factory.generateCertPath(Collections.singletonList(deviceCertificate));
            final CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);
            LOG.debug("validation of device certificate [subject DN: {}] succeeded", deviceCertificate.getSubjectX500Principal().getName());
            result.complete();
        } catch (GeneralSecurityException e) {
            LOG.debug("validation of device certificate [subject DN: {}] failed", deviceCertificate.getSubjectX500Principal().getName(), e);
            result.fail(e);
        }
        return result;
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}