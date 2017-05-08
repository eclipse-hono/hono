/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.config;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

/**
 * Common properties required for creating/validating cryptographic signatures.
 *
 */
public abstract class SignatureSupportingConfigProperties {

    private byte[] signingSecret;
    protected String signatureAlgorithm;

    /**
     * Sets the secret to use for validating creating/validating HmacSHA256 based signatures.
     * 
     * @param secret The shared secret.
     * @throws NullPointerException if secret is {@code null}.
     */
    public final void setSigningSecret(final String secret) {
        this.signingSecret = Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8);
        this.signatureAlgorithm = "HmacSHA256";
    }

    protected final Key getHmacKey() {
        if (signingSecret != null) {
            return new SecretKeySpec(signingSecret, signatureAlgorithm);
        } else {
            return null;
        }
    }

    /**
     * Gets the secret used for validating HMAC based signatures.
     * 
     * @return The secret or {@code null} if not set.
     */
    public final byte [] getSigningSecret() {
        return signingSecret;
    }

    /**
     * Gets the algorithm to use for validating signatures.
     * 
     * @return The JCA algorithm name.
     */
    public final String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }
}