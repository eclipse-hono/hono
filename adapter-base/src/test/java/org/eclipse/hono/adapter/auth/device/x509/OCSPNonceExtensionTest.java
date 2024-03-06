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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DLSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying {@link OCSPNonceExtension} class.
 *
 */
class OCSPNonceExtensionTest {

    private static final String NONCE_EXTENSION_ID = "1.3.6.1.5.5.7.48.1.2";

    private OCSPNonceExtension nonceExtension;

    @BeforeEach
    void setUp() throws IOException {
        nonceExtension = new OCSPNonceExtension();
    }

    /**
     * Verify that extension ID matches the nonce extension ID.
     */
    @Test
    void testGetId() {
        final String id = nonceExtension.getId();
        assertThat(id).isEqualTo(NONCE_EXTENSION_ID);
    }

    /**
     * Check that encoded extension value is valid DER encoded object with expected identifier
     * and valid DER OctetString.
     */
    @Test
    void testEncodeWritesParsableDerObject() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            nonceExtension.encode(out);
            try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
                final ASN1InputStream asnInputStream = new ASN1InputStream(in);
                final ASN1Primitive asn1 = asnInputStream.readObject();
                assertThat(asn1).isInstanceOf(DLSequence.class);
                assertThat(((DLSequence) asn1).size()).isEqualTo(2);
                final ASN1Encodable firstElement = ((DLSequence) asn1).getObjectAt(0);
                assertThat(firstElement).isInstanceOf(ASN1ObjectIdentifier.class);
                assertThat(((ASN1ObjectIdentifier) firstElement).getId()).isEqualTo(NONCE_EXTENSION_ID);
                final ASN1Encodable secondElement = ((DLSequence) asn1).getObjectAt(1);
                assertThat(secondElement).isInstanceOf(DEROctetString.class);
                assertThat(((DEROctetString) secondElement).getOctets()).isEqualTo(nonceExtension.getValue());
            }
        }
    }
}
