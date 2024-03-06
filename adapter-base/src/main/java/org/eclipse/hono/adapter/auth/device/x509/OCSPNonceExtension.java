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

import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;

/**
 * Implementation of OCSP nonce extension (RFC 8954) which is used to avoid replay attacks to OCSP requests.
 * We need custom implementation, because the Java implementation if from legacy sun package.
 */
public final class OCSPNonceExtension implements java.security.cert.Extension {
    /**
     * According to RFC 8954 must be from 1 to 32.
     */
    private static final int OCSP_NONCE_SIZE = 16;
    private final byte[] value;
    private final Extension extension;

    /**
     * Creates a new instance of OCSP nonce extension.
     *
     * @throws IOException on failure when encoding nonce value.
     */
    public OCSPNonceExtension() throws IOException {
        final SecureRandom random = new SecureRandom();
        final byte[] nonce = new byte[OCSP_NONCE_SIZE];
        random.nextBytes(nonce);
        final DEROctetString derValue = new DEROctetString(nonce);
        value = derValue.getEncoded();
        extension = Extension.create(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, false, derValue);
    }

    @Override
    public String getId() {
        return OCSPObjectIdentifiers.id_pkix_ocsp_nonce.getId();
    }

    @Override
    public boolean isCritical() {
        return false;
    }

    @Override
    public byte[] getValue() {
        return value;
    }

    @Override
    public void encode(final OutputStream out) throws IOException {
        extension.encodeTo(out);
    }
}
