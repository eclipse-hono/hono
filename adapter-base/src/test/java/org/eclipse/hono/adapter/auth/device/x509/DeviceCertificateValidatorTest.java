/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.security.cert.TrustAnchor;
import java.util.List;

import org.eclipse.hono.test.VertxTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link DeviceCertificateValidator}.
 *
 */
@ExtendWith(VertxExtension.class)
class DeviceCertificateValidatorTest {

    private DeviceCertificateValidator validator;

    /**
     */
    @BeforeEach
    void setUp() {
        validator = new DeviceCertificateValidator();
    }

    /**
     * Verifies that the validator succeeds to verify a certificate chain
     * using a trust anchor that has been created with a name and public key
     * instead of a certificate.
     *
     * @param vertx The vert.x instance to use.
     * @param ctx The vert.x test context.
     */
    @Test
    void testValidateSucceedsForTrustAnchorBasedOnPublicKey(final Vertx vertx, final VertxTestContext ctx) {

        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create("iot.eclipse.org");
        VertxTools.getCertificate(vertx, deviceCert.certificatePath())
            .compose(cert -> {
                final TrustAnchor ca = new TrustAnchor(cert.getSubjectX500Principal(), cert.getPublicKey(), null);
                return validator.validate(List.of(cert), ca);
            })
            .onComplete(ctx.succeedingThenComplete());
    }
}
