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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Period;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Tests verifying behavior of {@link TenantTraceSamplingHandler}.
 */
public class TenantTraceSamplingHandlerTest {

    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    private TenantClient tenantClient;
    private ProtocolAdapterProperties config;
    private Span span;
    private RoutingContext ctx;
    private Map<String, Object> ctxMap;
    private TenantTraceSamplingHandler tenantTraceSamplingHandler;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        tenantClient = mock(TenantClient.class);
        doAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        }).when(tenantClient).get(anyString(), any(SpanContext.class));

        final TenantClientFactory tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(tenantClientFactory).disconnect(any(Handler.class));
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        config = new ProtocolAdapterProperties();

        span = mock(Span.class);
        final SpanContext spanContext = mock(SpanContext.class);
        when(span.context()).thenReturn(spanContext);

        ctxMap = new HashMap<>();
        ctxMap.put(TracingHandler.CURRENT_SPAN, span);

        ctx = mock(RoutingContext.class);
        when(ctx.get(anyString())).thenAnswer(invocation -> {
            return ctxMap.get(invocation.getArgument(0));
        });

        final HttpContextTenantAndAuthIdProvider tenantAndAuthIdProvider = new HttpContextTenantAndAuthIdProvider(
                config, tenantClientFactory, PARAM_TENANT, PARAM_DEVICE_ID);
        tenantTraceSamplingHandler = new TenantTraceSamplingHandler(tenantAndAuthIdProvider);
    }

    /**
     * Verifies that the handler sets the appropriate span tag for a request for a tenant with 'trace-sampling-mode' set
     * to 'all'.
     */
    @Test
    public void testHandleSetsSamplingPriorityForMatchingTenant() {
        // GIVEN a tenant where 'trace-sampling-mode' is set to 'all'
        final String tenantId = "testTenant";
        final String authId = "testAuthId";

        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig().setSamplingMode(TracingSamplingMode.ALL));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(testTenant));

        // WHEN handling a request with basic auth for that tenant
        setupBasicAuthHttpServerRequest(tenantId, authId);
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(ctx).next();
    }

    /**
     * Verifies that the handler sets the appropriate span tag for a request with a device auth id for which the trace
     * sampling mode is set to 'all'.
     */
    @Test
    public void testHandleSetsSamplingPriorityForMatchingAuthId() {
        // GIVEN a tenant and auth-id for which 'trace-sampling-mode-per-device' is set to 'all'
        final String tenantId = "testTenant";
        final String authId = "testAuthId";

        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig()
                        .setSamplingModePerAuthId(Map.of(authId, TracingSamplingMode.ALL)));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(testTenant));

        // WHEN handling a request with basic auth for that tenant
        setupBasicAuthHttpServerRequest(tenantId, authId);
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(ctx).next();
    }

    /**
     * Verifies that the handler does nothing for a request with a device auth id for which the trace sampling mode is
     * set to 'default'.
     */
    @Test
    public void testHandleRespectsOverrideForAuthId() {
        // GIVEN a tenant and auth-id where 'trace-sampling-mode' is set to 'all'
        // and 'trace-sampling-mode-per-device' is set to 'default'
        final String tenantId = "testTenant";
        final String authId = "testAuthId";

        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig()
                        .setSamplingMode(TracingSamplingMode.ALL)
                        .setSamplingModePerAuthId(Map.of(authId, TracingSamplingMode.DEFAULT)));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(testTenant));

        // WHEN handling a request with basic auth for that tenant
        setupBasicAuthHttpServerRequest(tenantId, authId);
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span, never()).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), anyInt());
        verify(ctx).next();
    }

    private void setupBasicAuthHttpServerRequest(final String tenantId, final String authId) {
        setupBasicAuthHttpServerRequest(authId + "@" + tenantId);
    }

    private void setupBasicAuthHttpServerRequest(final String userName) {
        final String authorization = "BASIC " +
                Base64.getEncoder().encodeToString((userName + ":password").getBytes(StandardCharsets.UTF_8));
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.headers()).thenReturn(headers);
        when(ctx.request()).thenReturn(req);
    }

    /**
     * Verifies that the handler sets the appropriate span tag for an unauthenticated request with tenant and device
     * parameters where the tenant is configured with 'trace-sampling-mode' set to 'all'.
     */
    @Test
    public void testHandleSetsSamplingPriorityForGivenTenantParam() {
        // GIVEN a tenant where 'trace-sampling-mode' is set to 'all'
        final String tenantId = "testTenant";
        final String authId = "testAuthId";
        ctxMap.put(PARAM_TENANT, tenantId);
        ctxMap.put(PARAM_DEVICE_ID, authId);

        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig().setSamplingMode(TracingSamplingMode.ALL));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(testTenant));

        // WHEN handling an unauthenticated request for that tenant
        config.setAuthenticationRequired(false);
        setupNonSslHttpRequest();
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(ctx).next();
    }

    /**
     * Verifies that the handler sets the appropriate span tag for an unauthenticated request with tenant and device
     * parameters where the device auth id is configured in the tenant config with a trace sampling mode set to 'all'.
     */
    @Test
    public void testHandleSetsSamplingPriorityForGivenDeviceParam() {
        // GIVEN a tenant and device auth-id for which 'trace-sampling-mode-per-device' is set to 'all'
        final String tenantId = "testTenant";
        final String authId = "testAuthId";
        ctxMap.put(PARAM_TENANT, tenantId);
        ctxMap.put(PARAM_DEVICE_ID, authId);

        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig()
                        .setSamplingModePerAuthId(Map.of(authId, TracingSamplingMode.ALL)));
        when(tenantClient.get(eq(tenantId), any(SpanContext.class))).thenReturn(Future.succeededFuture(testTenant));

        // WHEN handling an unauthenticated request for that tenant
        config.setAuthenticationRequired(false);
        setupNonSslHttpRequest();
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(ctx).next();
    }

    private void setupNonSslHttpRequest() {
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.isSSL()).thenReturn(false);
        when(ctx.request()).thenReturn(req);
    }

    /**
     * Verifies that the handler sets the appropriate span tag for a request with a certificate mapped to a tenant that
     * has trace sampling mode set to 'all'.
     *
     * @throws SSLPeerUnverifiedException if the client certificate cannot be validated.
     */
    @Test
    public void testHandleSetsSamplingPriorityForGivenCert() throws SSLPeerUnverifiedException {
        // GIVEN a tenant where 'trace-sampling-mode' is set to 'all'
        final String tenantId = "testTenant";
        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig()
                        .setSamplingMode(TracingSamplingMode.ALL));
        doAnswer(invocation -> {
            if (!invocation.getArgument(0).toString().equals("CN=" + tenantId)) {
                return Future.failedFuture("tenant not found");
            }
            return Future.succeededFuture(testTenant);
        }).when(tenantClient).get(any(X500Principal.class), any(SpanContext.class));

        // WHEN trying to authenticate a request that contains a client certificate associated with that tenant
        setupClientCertHttpRequest(tenantId, "CN=device");
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(ctx).next();
    }

    /**
     * Verifies that the handler sets the appropriate span tag for a request with a certificate where the mapped tenant
     * has a trace sampling mode 'all' configured for the cert subject-dn as auth-id.
     *
     * @throws SSLPeerUnverifiedException if the client certificate cannot be validated.
     */
    @Test
    public void testHandleSetsSamplingPriorityForGivenCertUsingSubjectDn() throws SSLPeerUnverifiedException {
        // GIVEN a tenant and auth-id for which 'trace-sampling-mode-per-device' is set to 'all'
        final String tenantId = "testTenant";
        final String subjectDnAuthId = "CN=Device 4711,OU=Hono,O=Eclipse IoT,L=Ottawa,C=CA";
        final TenantObject testTenant = TenantObject.from(tenantId, true)
                .setTracingConfig(new TenantTracingConfig()
                        .setSamplingModePerAuthId(Map.of(subjectDnAuthId, TracingSamplingMode.ALL)));
        doAnswer(invocation -> {
            if (!invocation.getArgument(0).toString().equals("CN=" + tenantId)) {
                return Future.failedFuture("tenant not found");
            }
            return Future.succeededFuture(testTenant);
        }).when(tenantClient).get(any(X500Principal.class), any(SpanContext.class));

        // WHEN trying to authenticate a request that contains a client certificate associated with that tenant and the auth-id
        setupClientCertHttpRequest(tenantId, subjectDnAuthId);
        tenantTraceSamplingHandler.handle(ctx);

        // THEN the 'sampling.priority' priority was set on the span
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(ctx).next();
    }

    private void setupClientCertHttpRequest(final String tenantId, final String subjectDnAuthId) throws SSLPeerUnverifiedException {
        final EmptyCertificate clientCert = new EmptyCertificate(subjectDnAuthId, "CN=" + tenantId);
        final SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new X509Certificate[]{clientCert});
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.isSSL()).thenReturn(true);
        when(req.sslSession()).thenReturn(sslSession);
        when(ctx.request()).thenReturn(req);
    }

    /**
     * An X.509 certificate which contains a subject and issuer only.
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
        public void verify(final PublicKey key)
                throws java.security.cert.CertificateException, NoSuchAlgorithmException,
                InvalidKeyException, NoSuchProviderException, SignatureException {
        }

        @Override
        public void verify(final PublicKey key, final String sigProvider)
                throws java.security.cert.CertificateException,
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
