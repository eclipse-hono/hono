/**
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.x509.SubjectDnCredentials;
import org.eclipse.hono.adapter.auth.device.x509.X509Authentication;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttEndpoint;


/**
 * Tests verifying behavior of {@link X509AuthHandler}.
 *
 */
@ExtendWith(VertxExtension.class)
public class X509AuthHandlerTest {

    private X509AuthHandler authHandler;
    private DeviceCredentialsAuthProvider<SubjectDnCredentials> authProvider;
    private X509Authentication clientAuth;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        clientAuth = mock(X509Authentication.class);
        authProvider = mock(DeviceCredentialsAuthProvider.class);
        authHandler = new X509AuthHandler(clientAuth, authProvider);
        span = TracingMockSupport.mockSpan();
    }

    /**
     * Verifies that the handler includes the MQTT client identifier in the authentication
     * information retrieved from a device's CONNECT packet.
     *
     * @param ctx The vert.x test context.
     * @throws SSLPeerUnverifiedException if the client certificate cannot be determined.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testParseCredentialsIncludesMqttClientId(final VertxTestContext ctx) throws SSLPeerUnverifiedException {

        // GIVEN an auth handler configured with an auth provider
        final JsonObject authInfo = new JsonObject()
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=device")
                .put(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID, "tenant");
        when(clientAuth.validateClientCertificate(
                any(Certificate[].class),
                any(List.class),
                (SpanContext) any()))
        .thenReturn(Future.succeededFuture(authInfo));

        // WHEN trying to authenticate a request that contains a client certificate
        final X509Certificate clientCert = getClientCertificate("CN=device", "CN=tenant");
        final SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new X509Certificate[] { clientCert });

        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isSsl()).thenReturn(true);
        when(endpoint.sslSession()).thenReturn(sslSession);
        when(endpoint.clientIdentifier()).thenReturn("mqtt-device");

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
            // THEN the auth info is correctly retrieved from the client certificate
            .onComplete(ctx.succeeding(info -> {
                ctx.verify(() -> {
                    assertThat(info.getString(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN)).isEqualTo("CN=device");
                    assertThat(info.getString(RequestResponseApiConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo("tenant");
                    assertThat(info.getString(X509AuthHandler.PROPERTY_CLIENT_IDENTIFIER)).isEqualTo("mqtt-device");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed Tenant service invocation in the response.
     *
     * @param ctx The vert.x test context.
     * @throws SSLPeerUnverifiedException if the client certificate cannot be determined.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider(final VertxTestContext ctx) throws SSLPeerUnverifiedException {

        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        when(clientAuth.validateClientCertificate(
                any(Certificate[].class),
                any(List.class),
                (SpanContext) any()))
        .thenReturn(Future.failedFuture(error));

        // WHEN trying to authenticate a request that contains a client certificate
        final X509Certificate clientCert = getClientCertificate("CN=device", "CN=tenant");
        final SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getPeerCertificates()).thenReturn(new X509Certificate[] { clientCert });

        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isSsl()).thenReturn(true);
        when(endpoint.sslSession()).thenReturn(sslSession);

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.authenticateDevice(context)
            // THEN the request context is failed with the 503 error code
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isEqualTo(error);
                });
                ctx.completeNow();
            }));
    }

    @SuppressWarnings("deprecation")
    private static X509Certificate getClientCertificate(final String subject, final String issuer) {

        final X509Certificate cert = mock(X509Certificate.class);
        final X500Principal subjectDn = new X500Principal(subject);
        final X500Principal issuerDn = new X500Principal(issuer);
        when(cert.getSubjectDN()).thenReturn(subjectDn);
        when(cert.getSubjectX500Principal()).thenReturn(subjectDn);
        when(cert.getIssuerDN()).thenReturn(issuerDn);
        when(cert.getIssuerX500Principal()).thenReturn(issuerDn);
        return cert;

    }
}
