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

package org.eclipse.hono.adapter.auth.device.x509;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;

/**
 * Tests verifying behavior of {@link TenantServiceBasedX509Authentication}.
 */
class TenantServiceBasedX509AuthenticationTest {

    private static X509Certificate cert;
    private static Certificate[] certPath;

    private TenantServiceBasedX509Authentication underTest;
    private TenantClient tenantClient;

    @BeforeAll
    static void createClientCertificate() throws GeneralSecurityException, IOException {

        final SelfSignedCertificate ssc = SelfSignedCertificate.create("eclipse.org");
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        cert = (X509Certificate) factory.generateCertificate(new FileInputStream(ssc.certificatePath()));
        certPath = new Certificate[] { cert };
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpFixture() {

        tenantClient = mock(TenantClient.class);
        final var validator = mock(X509CertificateChainValidator.class);
        when(validator.validate(any(List.class), any(Set.class))).thenReturn(Future.succeededFuture());

        underTest = new TenantServiceBasedX509Authentication(
                tenantClient,
                NoopTracerFactory.create(),
                validator);
    }

    /**
     * Verifies that the the host name from the client's SNI TLS extension is used to look
     * up the tenant if using the client cert's issuer DN does not work.
     */
    @Test
    void testValidateClientCertificateUsesRequestedHostName() {

        // GIVEN a tenant that cannot be looked up via trust anchor subject DN
        final TenantObject tenant = TenantObject.from("tenant", true)
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), false);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any()))
            .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        when(tenantClient.get(anyString(), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating a client's certificate from a TLS session that contains
        // a requested host name
        final Future<JsonObject> jsonObjectFuture = underTest.validateClientCertificate(
                certPath,
                List.of("tenant.hono.eclipse.org"),
                null);
        assertThat(jsonObjectFuture.succeeded()).isTrue();

        // THEN the returned JSON object contains the authentication information
        verify(tenantClient).get(any(X500Principal.class), any());
        verify(tenantClient).get(eq("tenant"), any());
        assertResponseContainsStandardProperties(jsonObjectFuture.result());
    }

    /**
     * Verifies that the the host name from the client's SNI TLS extension is ignored if it
     * consists of a single label only.
     */
    @Test
    void testValidateClientCertificateIgnoresSingleLabelRequestedHostName() {

        // GIVEN a tenant that cannot be looked up via trust anchor subject DN
        final TenantObject tenant = TenantObject.from("tenant", true)
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), false);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any()))
            .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        when(tenantClient.get(anyString(), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating a client's certificate from a TLS session that contains
        // a requested host name
        final Future<JsonObject> result = underTest.validateClientCertificate(
                certPath,
                List.of("localhost"),
                null);

        // THEN validation fails with a 400
        verify(tenantClient).get(any(X500Principal.class), any());
        verify(tenantClient, never()).get(anyString(), any());
        assertThat(result.succeeded()).isFalse();
        assertThat(ServiceInvocationException.extractStatusCode(result.cause()))
            .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that when the trust anchor is enabled for auto-provisioning the certificate is put into the JSON object
     * used to query the credentials from the credentials API.
     *
     * @throws GeneralSecurityException if the certificate can not be read.
     */
    @Test
    void testValidateClientCertificateContainsReadableCertificate() throws GeneralSecurityException {

        // GIVEN a trust anchor that is enabled for auto-provisioning
        final TenantObject tenant = TenantObject.from("tenant", true)
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), true);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating the client certificate
        final Future<JsonObject> jsonObjectFuture = underTest.validateClientCertificate(certPath, List.of(), null);
        assertThat(jsonObjectFuture.succeeded()).isTrue();

        // THEN the returned JSON object contains the client certificate
        assertResponseContainsStandardProperties(jsonObjectFuture.result());
        assertThat(jsonObjectFuture.result().getBinary("client-certificate")).isEqualTo(cert.getEncoded());
    }

    /**
     * Verifies that when the trust anchor is not enabled for auto-provisioning the certificate is not contained in the
     * JSON object used to query the credentials from the credentials API.
     */
    @Test
    void testValidateClientCertificateContainsNoCertificate() {

        // GIVEN a trust anchor that is disabled for auto-provisioning
        final TenantObject tenant = TenantObject.from("tenant", true)
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), false);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating the client certificate
        final Future<JsonObject> jsonObjectFuture = underTest.validateClientCertificate(certPath, List.of(), null);
        assertThat(jsonObjectFuture.succeeded()).isTrue();

        // THEN the returned JSON object does not contain the client certificate
        assertResponseContainsStandardProperties(jsonObjectFuture.result());
        assertThat(jsonObjectFuture.result().containsKey("client-certificate")).isFalse();
    }

    private void assertResponseContainsStandardProperties(final JsonObject response) {
        assertThat(response.getString("subject-dn"))
            .isEqualTo(cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        assertThat(response.getString("tenant-id")).isEqualTo("tenant");
    }
}
