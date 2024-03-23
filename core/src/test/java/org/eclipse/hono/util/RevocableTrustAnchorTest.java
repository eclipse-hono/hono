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

package org.eclipse.hono.util;

import static com.google.common.truth.Truth.assertThat;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import org.eclipse.hono.test.VertxTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying {@link RevocableTrustAnchor} class which holds information about
 * trust anchor and revocation properties.
 *
 */
@ExtendWith(VertxExtension.class)
class RevocableTrustAnchorTest {

    /**
     * Verifies that the constructor correctly parses and setup revocation properties
     * from JSON object.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    void testConstructorLoadingJsonProperties(final Vertx vertx, final VertxTestContext ctx) {
        final SelfSignedCertificate anotherCaCert = SelfSignedCertificate.create("example.com");
        VertxTools.getCertificate(vertx, anotherCaCert.certificatePath())
            .onSuccess((cert) -> {
                final JsonObject trustedCAProps = new JsonObject()
                        .put(RegistryManagementConstants.FIELD_OCSP_REVOCATION_ENABLED, true)
                        .put(RegistryManagementConstants.FIELD_OCSP_RESPONDER_URI, "http://example.com:8080/")
                        .put(RegistryManagementConstants.FIELD_OCSP_RESPONDER_CERT, getEncodedCertificate(cert))
                        .put(RegistryManagementConstants.FIELD_OCSP_NONCE_ENABLED, true);
                final RevocableTrustAnchor anchor = new RevocableTrustAnchor(cert.getSubjectX500Principal(),
                        cert.getPublicKey(), null, trustedCAProps);
                ctx.verify(() -> {
                        assertThat(anchor.isOcspEnabled()).isTrue();
                        assertThat(anchor.getOcspResponderUri().getHost()).isEqualTo("example.com");
                        assertThat(anchor.getOcspResponderUri().getScheme()).isEqualTo("http");
                        assertThat(anchor.getOcspResponderUri().getPort()).isEqualTo(8080);
                        assertThat(anchor.getOcspResponderCert()).isEqualTo(cert);
                        assertThat(anchor.isOcspNonceEnabled()).isTrue();
                        ctx.completeNow();
                });
            });
    }

    private byte[] getEncodedCertificate(final X509Certificate cert) {
        try {
            return cert.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
