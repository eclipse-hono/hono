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


package org.eclipse.hono.service.management.tenant;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
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
 * Tests verifying behavior of {@link TrustedCertificateAuthority}.
 *
 */
class TrustedCertificateAuthorityTest {

    private static X509Certificate certificate;

    /**
     * Sets up class fixture.
     *
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
                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN_BYTES, certificate.getSubjectX500Principal().getEncoded())
                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY, certificate.getPublicKey().getEncoded())
                .put(RegistryManagementConstants.FIELD_PAYLOAD_KEY_ALGORITHM, certificate.getPublicKey().getAlgorithm())
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE, DateTimeFormatter.ISO_INSTANT.format(notBefore))
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, DateTimeFormatter.ISO_INSTANT.format(notAfter))
                .put(RegistryManagementConstants.FIELD_AUTO_PROVISION_AS_GATEWAY, true)
                .put(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE,
                        "device-" + RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN);

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);
        assertThat(authority.isValid()).isTrue();
        assertThat(authority.isAutoProvisioningAsGatewayEnabled()).isTrue();
        assertThat(authority.getAutoProvisioningDeviceIdTemplate())
                .isEqualTo("device-" + RegistryManagementConstants.PLACEHOLDER_SUBJECT_CN);
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
        assertThat(authority.isAutoProvisioningAsGatewayEnabled()).isFalse();
        assertThat(authority.getAutoProvisioningDeviceIdTemplate()).isNull();
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
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER, DateTimeFormatter.ISO_INSTANT.format(notAfter))
                .put(RegistryManagementConstants.FIELD_AUTO_PROVISION_AS_GATEWAY, true);

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);
        assertAuthority(authority);
        assertThat(authority.isAutoProvisioningAsGatewayEnabled()).isTrue();
    }

    /**
     * Verifies that decoding of a trusted CA entry containing invalid
     * {@value RegistryManagementConstants#FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE} value fails.
     */
    @Test
    public void testDecodeTrustedCAWithInvalidDeviceIdTemplate() {
        final Instant notBefore = certificate.getNotBefore().toInstant();
        final Instant notAfter = certificate.getNotAfter().toInstant();
        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_SUBJECT_DN,
                        certificate.getSubjectX500Principal().getName(X500Principal.RFC2253))
                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY, certificate.getPublicKey().getEncoded())
                .put(RegistryManagementConstants.FIELD_PAYLOAD_KEY_ALGORITHM, certificate.getPublicKey().getAlgorithm())
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE,
                        DateTimeFormatter.ISO_INSTANT.format(notBefore))
                .put(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER,
                        DateTimeFormatter.ISO_INSTANT.format(notAfter))
                .put(RegistryManagementConstants.FIELD_AUTO_PROVISION_AS_GATEWAY, true)
                .put(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE, "device");

        assertThrows(IllegalArgumentException.class, () -> ca.mapTo(TrustedCertificateAuthority.class));
    }

    /**
     * Verifies that decoding of a trusted CA entry containing OCSP revocation check settings.
     *
     * @throws CertificateException if the certificate cannot be encoded.
     */
    @Test
    public void testDecodeTrustedCAWithOCSPRevocationEnabled() throws CertificateException {
        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_OCSP_REVOCATION_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_OCSP_RESPONDER_URI, "http://example.com:8080/")
                .put(RegistryManagementConstants.FIELD_OCSP_RESPONDER_CERT, certificate.getEncoded())
                .put(RegistryManagementConstants.FIELD_OCSP_NONCE_ENABLED, false);

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);

        assertThat(authority.isOcspRevocationEnabled()).isTrue();
        assertThat(authority.getOcspResponderUri()).isEqualTo("http://example.com:8080/");
        assertThat(authority.getOcspResponderCert()).isEqualTo(certificate.getEncoded());
        assertThat(authority.isOcspNonceEnabled()).isFalse();
    }

    /**
     * Verifies that trusted CA certificate is used as default OCSP responder certificate when it is not
     * explicitly set.
     *
     * @throws CertificateException if the certificate cannot be encoded.
     */
    @Test
    public void testDecodeTrustedCAWithMissingOCSPResponderCert() throws CertificateException {
        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_OCSP_REVOCATION_ENABLED, true)
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT, certificate.getEncoded());

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);

        assertThat(authority.getOcspResponderCert()).isEqualTo(certificate.getEncoded());
    }

    /**
     * Verifies that authority is not valid when OCSP is enabled but no certificate for signature
     * verification is set.
     */
    @Test
    public void testIsValidWithMissingOCSPResponderCertAndCACert() {
        final JsonObject ca = new JsonObject()
                .put(RegistryManagementConstants.FIELD_OCSP_REVOCATION_ENABLED, true);

        final TrustedCertificateAuthority authority = ca.mapTo(TrustedCertificateAuthority.class);

        assertThat(authority.isValid()).isFalse();
    }

    private void assertAuthority(final TrustedCertificateAuthority authority) {
        assertThat(authority.isValid()).isTrue();
        assertThat(authority.getSubjectDn()).isEqualTo(certificate.getSubjectX500Principal());
        assertThat(authority.getSubjectDnBytes()).isEqualTo(certificate.getSubjectX500Principal().getEncoded());
        assertThat(authority.getPublicKey()).isEqualTo(certificate.getPublicKey().getEncoded());
        assertThat(authority.getKeyAlgorithm()).isEqualTo(certificate.getPublicKey().getAlgorithm());
        assertThat(authority.getNotBefore()).isEqualTo(certificate.getNotBefore().toInstant());
        assertThat(authority.getNotAfter()).isEqualTo(certificate.getNotAfter().toInstant());
    }
}
