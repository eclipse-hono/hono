/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        assertEquals(NONCE_EXTENSION_ID, id);
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
                assertInstanceOf(DLSequence.class, asn1);
                assertEquals(2, ((DLSequence) asn1).size());
                final ASN1Encodable firstElement = ((DLSequence) asn1).getObjectAt(0);
                assertInstanceOf(ASN1ObjectIdentifier.class, firstElement);
                assertEquals(NONCE_EXTENSION_ID, ((ASN1ObjectIdentifier) firstElement).getId());
                final ASN1Encodable secondElement = ((DLSequence) asn1).getObjectAt(1);
                assertInstanceOf(DEROctetString.class, secondElement);
            }
        }
    }
}
