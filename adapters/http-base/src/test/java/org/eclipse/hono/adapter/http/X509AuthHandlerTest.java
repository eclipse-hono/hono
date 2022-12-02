/**
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.x509.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.x509.X509Authentication;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.http.HttpServerSpanHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;


/**
 * Tests verifying behavior of {@link X509AuthHandler}.
 *
 */
public class X509AuthHandlerTest {

    private X509AuthHandler authHandler;
    private DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider;
    private X509Authentication clientAuth;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        clientAuth = mock(X509Authentication.class);
        authProvider = mock(DeviceCredentialsAuthProvider.class);
        authHandler = new X509AuthHandler(clientAuth, authProvider, null);
    }

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed Tenant service invocation in the response.
     *
     * @throws SSLPeerUnverifiedException if the client certificate cannot be validated.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() throws SSLPeerUnverifiedException {

        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        when(clientAuth.validateClientCertificate(
                any(Certificate[].class),
                any(List.class),
                (SpanContext) any()))
            .thenReturn(Future.failedFuture(error));

        // WHEN trying to authenticate a request that contains a client certificate
        final EmptyCertificate clientCert = new EmptyCertificate("CN=device", "CN=tenant");
        final SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new X509Certificate[] { clientCert });
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.isSSL()).thenReturn(true);
        when(req.sslSession()).thenReturn(sslSession);
        when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        final HttpServerResponse resp = mock(HttpServerResponse.class);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.get(HttpServerSpanHelper.ROUTING_CONTEXT_SPAN_KEY)).thenReturn(mock(Span.class));
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        authHandler.handle(ctx);

        // THEN the request context is failed with the 503 error code
        verify(ctx).fail(error);
    }

    /**
     * An X.509 certificate which contains a subject and issuer only.
     *
     */
    private static class EmptyCertificate extends X509Certificate {

        private final X500Principal subject;
        private final X500Principal issuer;

        /**
         * Creates a new certificate.
         *
         * @param subject The subject.
         * @param issuer The issuer of the certificate.
         */
        EmptyCertificate(final String subject, final String issuer) {
            this.subject = new X500Principal(subject);
            this.issuer = new X500Principal(issuer);
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(final String oid) {
            return null;
        }

        @Override
        public void checkValidity() throws java.security.cert.CertificateExpiredException,
                java.security.cert.CertificateNotYetValidException {
        }

        @Override
        public void checkValidity(final Date date) throws java.security.cert.CertificateExpiredException,
                java.security.cert.CertificateNotYetValidException {
        }

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public BigInteger getSerialNumber() {
            return null;
        }

        @Override
        public Principal getIssuerDN() {
            return issuer;
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return issuer;
        }

        @Override
        public Principal getSubjectDN() {
            return subject;
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return subject;
        }

        @Override
        public Date getNotBefore() {
            return Date.from(Instant.now().minus(Period.ofDays(1)));
        }

        @Override
        public Date getNotAfter() {
            return Date.from(Instant.now().plus(Period.ofDays(1)));
        }

        @Override
        public byte[] getTBSCertificate() throws java.security.cert.CertificateEncodingException {
            return null;
        }

        @Override
        public byte[] getSignature() {
            return null;
        }

        @Override
        public String getSigAlgName() {
            return null;
        }

        @Override
        public String getSigAlgOID() {
            return null;
        }

        @Override
        public byte[] getSigAlgParams() {
            return null;
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return 0;
        }

        @Override
        public byte[] getEncoded() throws java.security.cert.CertificateEncodingException {
            return null;
        }

        @Override
        public void verify(final PublicKey key) throws java.security.cert.CertificateException, NoSuchAlgorithmException,
                InvalidKeyException, NoSuchProviderException, SignatureException {
        }

        @Override
        public void verify(final PublicKey key, final String sigProvider) throws java.security.cert.CertificateException,
                NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {
        }

        @Override
        public String toString() {
            return null;
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }
    }
}
