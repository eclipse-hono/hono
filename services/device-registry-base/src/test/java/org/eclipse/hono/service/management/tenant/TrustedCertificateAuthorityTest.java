/**
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
 */


package org.eclipse.hono.service.management.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;


/**
 * A TrustedCertificateAuthorityTest.
 *
 */
class TrustedCertificateAuthorityTest {

    private static X509Certificate certificate;

    /**
     * Sets up class fixture.
     * @throws GeneralSecurityException if the self signed certificate cannot be created.
     * @throws IOException if the self signed certificate cannot be read.
     */
    @BeforeAll
    public static void setUp() throws GeneralSecurityException, IOException {
        final SelfSignedCertificate selfSignedCert = SelfSignedCertificate.create("eclipse.org");
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        certificate = (X509Certificate) factory.generateCertificate(new FileInputStream(selfSignedCert.certificatePath()));
    }

    /**
     * Decode "trusted-ca" section for a public key.
     */
    @Test
    public void testDecodeTrustedCAUsingPublicKey() {

        final Instant notBefore = certificate.getNotBefore().toInstant();
        final Instant notAfter = certificate.getNotAfter().toInstant();

        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN, certificate.getSubjectX500Principal().getName(X500Principal.RFC2253))
                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY, certificate.getPublicKey().getEncoded())
                .put(RegistryManagementConstants.FIELD_PAYLOAD_KEY_ALGORITHM, certificate.getPublicKey().getAlgorithm())
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, DateTimeFormatter.ISO_INSTANT.format(notBefore))
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, DateTimeFormatter.ISO_INSTANT.format(notAfter));

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);
        assertThat(authority.isValid()).isTrue();
        assertAuthority(authority);
    }

    /**
     * Decode "trusted-ca" section for an X.509 certificate.
     * 
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testDecodeTrustedCAUsingCert() throws CertificateEncodingException {

        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded());

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);
        assertThat(authority.isValid()).isTrue();
        assertAuthority(authority);
    }

    /**
     * Verifies that the subject DN and key algorithm are
     * derived from a certificate instead of explicitly specified values.
     * 
     * @throws CertificateEncodingException if the certificate cannot be encoded.
     */
    @Test
    public void testDecodeTrustedCAUsingCertAndPublicKey() throws CertificateEncodingException {

        final Instant notBefore = certificate.getNotBefore().toInstant().minus(1, ChronoUnit.DAYS);
        final Instant notAfter = certificate.getNotAfter().toInstant().plus(2, ChronoUnit.DAYS);

        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded())
                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NOTAKEY".getBytes())
                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=not the right subject")
                .put(RegistryManagementConstants.FIELD_PAYLOAD_KEY_ALGORITHM, "unsupported")
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, DateTimeFormatter.ISO_INSTANT.format(notBefore))
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, DateTimeFormatter.ISO_INSTANT.format(notAfter));

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);
        assertAuthority(authority);
    }

    private void assertAuthority(final TrustedCertificateAuthority authority) {
        assertThat(authority.isValid()).isTrue();
        assertThat(authority.getSubjectDn()).isEqualTo(certificate.getSubjectX500Principal());
        assertThat(authority.getPublicKey()).isEqualTo(certificate.getPublicKey().getEncoded());
        assertThat(authority.getKeyAlgorithm()).isEqualTo(certificate.getPublicKey().getAlgorithm());
        assertThat(authority.getNotBefore()).isEqualTo(certificate.getNotBefore().toInstant());
        assertThat(authority.getNotAfter()).isEqualTo(certificate.getNotAfter().toInstant());
    }
}
