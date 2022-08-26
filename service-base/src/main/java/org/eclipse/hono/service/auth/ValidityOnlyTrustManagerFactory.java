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

package org.eclipse.hono.service.auth;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.handler.ssl.util.SimpleTrustManagerFactory;


/**
 * A {@link javax.net.ssl.TrustManagerFactory} that trusts an X.509 certificate that is currently
 * valid according to its <em>not before</em> and <em>not after</em> properties.
 *
 */
@SuppressFBWarnings(
        value = "WEAK_TRUST_MANAGER",
        justification = """
                We cannot validate the certificate chain when the TLS connection gets established because we need to
                dynamically look up the trust anchors configured for the tenant that the client (device) belongs to.
                Therefore, the validation of the chain of trust is done at the application level, i.e. during connection
                establishment and/or request processing instead.
                """)
public final class ValidityOnlyTrustManagerFactory extends SimpleTrustManagerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ValidityOnlyTrustManagerFactory.class);
    private static final X509Certificate[] EMPTY_CERTS = new X509Certificate[0];

    private final TrustManager tm = new X509TrustManager() {

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return EMPTY_CERTS;
        }

        /**
         * {@inheritDoc}
         *
         * Always throws {@code UnsupportedOperationException}.
         */
        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {

            if (chain == null) {
                throw new NullPointerException("certificate chain must not be null");
            } else if (chain.length < 1) {
                throw new IllegalArgumentException("certificate chain must not be empty");
            } else {
                final X509Certificate deviceCert = chain[0];
                final Instant notBefore = deviceCert.getNotBefore().toInstant();
                final Instant notAfter = deviceCert.getNotAfter().toInstant();
                final Instant now = Instant.now();
                if (now.isBefore(notBefore)) {
                    throw new CertificateNotYetValidException();
                } else if (now.isAfter(notAfter)) {
                    throw new CertificateExpiredException();
                } else {
                    // certificate is valid, defer further checks to application layer
                    // where the certificate's signature should be validated using the
                    // tenant's root CA certificate
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("accepting client certificate [not before: {}, not after: {}, subject DN: {}, issuer DN: {}]",
                                notBefore, notAfter,
                                deviceCert.getSubjectX500Principal().getName(X500Principal.RFC2253),
                                deviceCert.getIssuerX500Principal().getName(X500Principal.RFC2253));
                    }
                }
            }
        }
    };

    @Override
    protected void engineInit(final KeyStore keyStore) throws Exception {
        // nothing to do
    }

    @Override
    protected void engineInit(final ManagerFactoryParameters managerFactoryParameters) throws Exception {
        // nothing to do
    }

    @Override
    protected TrustManager[] engineGetTrustManagers() {
        return new TrustManager[] { tm };
    }
}
