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
 * Properties required for creating cryptographic signatures.
 *
 */
public class SignatureCreationConfigProperties extends SignatureSupportingConfigProperties {

    private String keyPath;

    /**
     * Sets the path to the file containing the private key to be used
     * for creating SHA256withRSA based signatures.
     * <p>
     * The file must be in PKCS8 PEM format.
     * 
     * @param keyPath The path to the PEM file.
     * @throws NullPointerException if the path is {@code null}.
     */
    public final void setCertificatePath(final String keyPath) {
        this.keyPath = Objects.requireNonNull(keyPath);
        signatureAlgorithm = "SHA256withRSA";
    }

    /**
     * Gets the path to the file containing the private key to be used
     * for validating RSA based signatures.
     * 
     * @return The path to the file or {@code null} if not set.
     */
    public final String getKeyPath() {
        return keyPath;
    }

    /**
     * Gets the secret key to use for creating signatures.
     * 
     * @param vertx The vertx instance to use for loading the private key (if necessary).
     * @return The key.
     * @throws IllegalStateException if neither shared secret nor key path is set.
     */
    public final Key getKey(final Vertx vertx) {
        Key result = getHmacKey();
        if (result != null) {
            return result;
        } else if (getKeyPath() != null) {
            return KeyLoader.fromFiles(vertx, getKeyPath(), null).getPrivateKey();
        } else {
            throw new IllegalStateException("either shared secret or key path must be set");
        }
    }
}
