/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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

import java.security.Key;
import java.util.Objects;

import io.vertx.core.Vertx;


/**
 * Properties required for validating cryptographic signatures.
 *
 */
public class SignatureValidationConfigProperties extends SignatureSupportingConfigProperties {

    private String certificatePath;

    /**
     * Sets the path to the X.509 certificate containing the public key to be used
     * for validating SHA256withRSA based signatures.
     * <p>
     * The file must be in PKCS8 PEM format.
     * 
     * @param certPath The path to the PEM file.
     * @throws NullPointerException if the path is {@code null}.
     */
    public final void setCertificatePath(final String certPath) {
        this.certificatePath = Objects.requireNonNull(certPath);
        signatureAlgorithm = "SHA256withRSA";
    }

    /**
     * Gets the path to the X.509 certificate containing the public key to be used
     * for validating RSA based signatures.
     * 
     * @return The path to the file or {@code null} if not set.
     */
    public final String getCertificatePath() {
        return certificatePath;
    }

    /**
     * Gets the key to use for validating signatures.
     * 
     * @param vertx The vertx instance to use for loading the public key (if necessary).
     * @return The key.
     * @throws IllegalStateException if neither shared secret nor certificate path is set.
     */
    public final Key getKey(final Vertx vertx) {
        Key result = getHmacKey();
        if (result != null) {
            return result;
        } else if (getCertificatePath() != null) {
            return KeyLoader.fromFiles(vertx, null, getCertificatePath()).getPublicKey();
        } else {
            throw new IllegalStateException("either shared secret or certificate path must be set");
        }
    }
}
