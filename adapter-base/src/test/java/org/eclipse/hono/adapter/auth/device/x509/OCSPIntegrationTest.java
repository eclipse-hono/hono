/*******************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device.x509;

import static com.google.common.truth.Truth.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import org.eclipse.hono.test.VertxTools;
import org.eclipse.hono.util.RevocableTrustAnchor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.vertx.core.Vertx;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Integrations tests verifying the OCSP settings of {@link DeviceCertificateValidator}. The code is
 * tested against running instance of OCSP responder which is set up using openssl tool running in Testcontainers.
 *
 */
@Testcontainers
@ExtendWith(VertxExtension.class)
class OCSPIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(OCSPIntegrationTest.class);

    private static final X509Certificate VALID_CERTIFICATE = loadCertificate("target/certs/device-4711-cert.pem");

    private static final X509Certificate REVOKED_CERTIFICATE = loadCertificate("target/certs/device-4712-cert.pem");

    private static final X509Certificate CA_CERTIFICATE = loadCertificate("target/certs/default_tenant-cert.pem");

    private static final String OCSP_RESPONDER_URI = "http://127.0.0.1";

    private static final int OCSP_RESPONDER_PORT = 8080;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Container
    private static final GenericContainer OCSP_RESPONDER_CONTAINER = new GenericContainer(
        new ImageFromDockerfile()
                .withFileFromString("index.txt", createCAIndexFile())
                .withFileFromPath("cacert.pem", Path.of("target/certs/default_tenant-cert.pem"))
                .withFileFromPath("cakey.pem", Path.of("target/certs/default_tenant-key.pem"))
                .withDockerfileFromBuilder(builder ->
                        builder.from("alpine:3.19")
                                .workDir("/ocsp")
                                .copy("index.txt", "/ocsp/index.txt")
                                .copy("cacert.pem", "/ocsp/cacert.pem")
                                .copy("cakey.pem", "/ocsp/cakey.pem")
                                .run("apk add openssl")
                                .cmd("openssl", "ocsp", "-index", "index.txt", "-port",
                                        Integer.toString(OCSP_RESPONDER_PORT), "-rsigner", "cacert.pem", "-rkey",
                                        "cakey.pem", "-CA", "cacert.pem", "-text", "-ignore_err")
                                .expose(OCSP_RESPONDER_PORT)
                                .build()))
                .withExposedPorts(OCSP_RESPONDER_PORT)
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .waitingFor(Wait.forHttp("/").forPort(OCSP_RESPONDER_PORT));;

    private DeviceCertificateValidator validator;

    /**
     */
    @BeforeEach
    void setUp() {
        validator = new DeviceCertificateValidator();
    }

    /**
     * Verifies that the validator succeeds to verify a valid certificate when OCSP
     * revocation check is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateSucceedsForValidCertWhenOCSPCheckDisabled(final VertxTestContext ctx) throws CertificateException,
        IOException {
        final RevocableTrustAnchor anchor = createTrustAnchor();

        validator.validate(List.of(VALID_CERTIFICATE), anchor).onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the validator succeeds to verify a revoked certificate when OCSP
     * revocation check is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateSucceedsForRevokedCertWhenOCSPCheckDisabled(final VertxTestContext ctx)
        throws CertificateException, IOException {
        final RevocableTrustAnchor anchor = createTrustAnchor();

        validator.validate(List.of(REVOKED_CERTIFICATE), anchor).onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the validator succeeds to verify a valid certificate when OCSP
     * revocation check is enabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateSucceedsForValidOCSPCertificate(final VertxTestContext ctx) throws CertificateException, IOException {
        final RevocableTrustAnchor anchor = createTrustAnchor();
        anchor.setOcspEnabled(true);
        anchor.setOcspResponderUri(getOCSPResponderUri());
        anchor.setOcspResponderCert(CA_CERTIFICATE);
        anchor.setOcspNonceEnabled(true);

        validator.validate(List.of(VALID_CERTIFICATE), anchor).onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the validator succeeds to verify a valid certificate when OCSP
     * revocation check is enabled and another trust anchor without OCSP is configured.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateSucceedsWithAnotherAnchorWithoutOCSP(final Vertx vertx, final VertxTestContext ctx) throws CertificateException, IOException {
        final RevocableTrustAnchor anchor = createTrustAnchor();
        anchor.setOcspEnabled(true);
        anchor.setOcspResponderUri(getOCSPResponderUri());
        anchor.setOcspResponderCert(CA_CERTIFICATE);
        anchor.setOcspNonceEnabled(true);

        final SelfSignedCertificate anotherCaCert = SelfSignedCertificate.create("example.com");
        VertxTools.getCertificate(vertx, anotherCaCert.certificatePath())
            .compose((cert) -> {
                final TrustAnchor anotherAnchor = new RevocableTrustAnchor(cert.getSubjectX500Principal(),
                    cert.getPublicKey(), null);
                return validator.validate(List.of(VALID_CERTIFICATE), Set.of(anotherAnchor, anchor));
            })
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that untrusted certificate fails to verify also in case that OCSP is enabled.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateFailsForUntrustedCertificateWithOCSP(final Vertx vertx, final VertxTestContext ctx) throws CertificateException, IOException {
        final RevocableTrustAnchor anchor = createTrustAnchor();
        anchor.setOcspEnabled(true);
        anchor.setOcspResponderUri(getOCSPResponderUri());
        anchor.setOcspResponderCert(CA_CERTIFICATE);
        anchor.setOcspNonceEnabled(true);

        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create("iot.eclipse.org");
        VertxTools.getCertificate(vertx, deviceCert.certificatePath())
                .compose(cert -> {
                    return validator.validate(List.of(cert), anchor);
                })
                .onComplete(ctx.failingThenComplete());
    }

    /**
     * Verifies that the validator fails to verify a revoked certificate when OCSP
     * revocation check is enabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateFailsWithExceptionForRevokedOCSPCertificate(final VertxTestContext ctx) throws CertificateException, IOException {
        final RevocableTrustAnchor anchor = createTrustAnchor();
        anchor.setOcspEnabled(true);
        anchor.setOcspResponderUri(getOCSPResponderUri());
        anchor.setOcspResponderCert(CA_CERTIFICATE);
        anchor.setOcspNonceEnabled(true);

        validator.validate(List.of(REVOKED_CERTIFICATE), anchor).onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(CertificateException.class));
            ctx.completeNow();
        }));
    }

    private static RevocableTrustAnchor createTrustAnchor()
            throws CertificateException, IOException {
        // When principal is loaded from certificate it is encoded as DER PrintableString but when it is created
        // from string it is encoded as UTF8String internally, this causes inconsistent issuerNameHash in OCSP
        // request, which cannot be handled by OpenSSL responder.
        return new RevocableTrustAnchor(CA_CERTIFICATE.getSubjectX500Principal(), CA_CERTIFICATE.getPublicKey(), null);
    }

    private static X509Certificate loadCertificate(final String certFilePath) {
        try (InputStream certificateStream = new FileInputStream(certFilePath)) {
            final CertificateFactory fact = CertificateFactory.getInstance("X.509");
            return (X509Certificate) fact.generateCertificate(certificateStream);
        } catch (IOException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates OpenSSL CA index file for OCSP responder with valid and revoked certificates.
     * @return The content of the index file.
     */
    private static String createCAIndexFile() {
        final StringBuilder sb = new StringBuilder();
        sb.append(createCAIndexLine(VALID_CERTIFICATE, false));
        sb.append(createCAIndexLine(REVOKED_CERTIFICATE, true));
        return sb.toString();
    }

    private static String createCAIndexLine(final X509Certificate cert, final boolean isRevoked) {
        final String serialNumber = cert.getSerialNumber().toString(16).toUpperCase();
        final String expirationMillis = Long.toString(cert.getNotAfter().getTime());
        final String revocationMillis = isRevoked ? Long.toString(cert.getNotBefore().getTime()) : "";
        final String subjectDn = cert.getSubjectX500Principal().getName().replace(',', '/');
        final String status = isRevoked ? "R" : "V";
        return String.format("%s\t%s\t%s\t%s\tunknown\t%s\n", status, expirationMillis, revocationMillis, serialNumber,
                subjectDn);
    }

    private URI getOCSPResponderUri() {
        return URI.create(OCSP_RESPONDER_URI + ":" + OCSP_RESPONDER_CONTAINER.getMappedPort(OCSP_RESPONDER_PORT));
    }
}
