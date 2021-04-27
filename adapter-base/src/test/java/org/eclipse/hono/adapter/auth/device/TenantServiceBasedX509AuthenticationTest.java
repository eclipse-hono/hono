/**
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.service.auth.X509CertificateChainValidator;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;

/**
 * Tests verifying behavior of {@link TenantServiceBasedX509Authentication}.
 */
class TenantServiceBasedX509AuthenticationTest {

    private static TenantServiceBasedX509Authentication underTest;
    private static TenantClient tenantClient;
    private static X509Certificate cert;
    private static Certificate[] certPath;

    @SuppressWarnings("unchecked")
    @BeforeAll
    static void setUp() throws GeneralSecurityException, IOException {

        final SelfSignedCertificate ssc = SelfSignedCertificate.create("eclipse.org");
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        cert = (X509Certificate) factory.generateCertificate(new FileInputStream(ssc.certificatePath()));
        certPath = new Certificate[] { cert };

        tenantClient = mock(TenantClient.class);
        final var validator = mock(X509CertificateChainValidator.class);
        when(validator.validate(any(List.class), any(Set.class))).thenReturn(Future.succeededFuture());

        underTest = new TenantServiceBasedX509Authentication(
                tenantClient,
                NoopTracerFactory.create(),
                validator);
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
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), true, false);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating the client certificate
        final Future<JsonObject> jsonObjectFuture = underTest.validateClientCertificate(certPath, null);
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
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), false, false);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating the client certificate
        final Future<JsonObject> jsonObjectFuture = underTest.validateClientCertificate(certPath, null);
        assertThat(jsonObjectFuture.succeeded()).isTrue();

        // THEN the returned JSON object does not contain the client certificate
        assertResponseContainsStandardProperties(jsonObjectFuture.result());
        assertThat(jsonObjectFuture.result().containsKey("client-certificate")).isFalse();
    }

    /**
     * Verifies that when the trust anchor is enabled for auto-provisioning a unregistered device as a gateway, then
     * the corresponding flag is set in the JSON object used to query the credentials from the credentials API.
     */
    @Test
    void testAutoProvisionAsGatewayIsSet() {
        // GIVEN a trust anchor that is enabled for auto-provisioning a unregistered device that authenticate using a client certificate as a gateway.
        final TenantObject tenant = TenantObject.from("tenant", true)
                .addTrustAnchor(cert.getPublicKey(), cert.getSubjectX500Principal(), true, true);
        when(tenantClient.get(eq(cert.getIssuerX500Principal()), any())).thenReturn(Future.succeededFuture(tenant));

        // WHEN validating the client certificate
        final Future<JsonObject> jsonObjectFuture = underTest.validateClientCertificate(certPath, null);
        assertThat(jsonObjectFuture.succeeded()).isTrue();

        // THEN the returned JSON object contains the flag regarding auto-provisioning of unregistered devices as gateways
        assertResponseContainsStandardProperties(jsonObjectFuture.result());
        assertThat(jsonObjectFuture.result().getBoolean(TenantConstants.FIELD_AUTO_PROVISION_AS_GATEWAY)).isTrue();
    }

    private void assertResponseContainsStandardProperties(final JsonObject response) {
        assertThat(response.getString("subject-dn")).isEqualTo(cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        assertThat(response.getString("tenant-id")).isEqualTo("tenant");
    }
}
