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
import java.util.Objects;

/**
 * Common properties required for creating/validating cryptographic signatures.
 *
 */
public class SignatureSupportingConfigProperties {

    private String sharedSecret;
    private String keyPath;
    private long tokenExpirationSeconds = 600L;
    private String certificatePath;

    /**
     * Gets the secret used for creating and validating HmacSHA256 based signatures.
     * 
     * @return The secret or {@code null} if not set.
     */
    public final String getSharedSecret() {
        return sharedSecret;
    }

    /**
     * Sets the secret to use for creating and validating HmacSHA256 based signatures.
     * 
     * @param secret The shared secret.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret is &lt; 32 bytes.
     */
    public final void setSharedSecret(final String secret) {
        if (Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("shared secret must be at least 32 bytes");
        }
        this.sharedSecret = secret;
    }

    /**
     * Sets the path to the file containing the private key to be used
     * for creating SHA256withRSA based signatures.
     * <p>
     * The file must be in PKCS8 PEM format.
     * 
     * @param keyPath The path to the PEM file.
     * @throws NullPointerException if the path is {@code null}.
     */
    public final void setKeyPath(final String keyPath) {
        this.keyPath = Objects.requireNonNull(keyPath);
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
     * Gets the period of time after which tokens created using this configuration should expire.
     * 
     * @return The number of seconds after which tokens expire.
     */
    public final long getTokenExpiration() {
        return tokenExpirationSeconds;
    }

    /**
     * Sets the period of time after which tokens created using this configuration should expire.
     * <p>
     * The default value is 600 seconds (10 minutes).
     * 
     * @param seconds The number of seconds after which tokens expire.
     * @throws IllegalArgumentException if seconds is &lt;= 0.
     */
    public final void setTokenExpiration(final long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("token expiration must be > 0");
        }
        this.tokenExpirationSeconds = seconds;
    }

    /**
     * Sets the path to the X.509 certificate containing the public key to be used
     * for validating SHA256withRSA based signatures.
     * <p>
     * The file must be in PKCS8 PEM format.
     * 
     * @param certPath The path to the PEM file.
     * @throws NullPointerException if the path is {@code null}.
     */
    public final void setCertPath(final String certPath) {
        this.certificatePath = Objects.requireNonNull(certPath);
    }

    /**
     * Gets the path to the X.509 certificate containing the public key to be used
     * for validating RSA based signatures.
     * 
     * @return The path to the file or {@code null} if not set.
     */
    public final String getCertPath() {
        return certificatePath;
    }

    /**
     * Checks if this configuration contains enough information for creating assertions.
     * 
     * @return {@code true} if any of sharedSecret or keyPath is not {@code null}.
     */
    public final boolean isAppropriateForCreating() {
        return sharedSecret != null || keyPath != null;
    }

    /**
     * Checks if this configuration contains enough information for validating assertions.
     * 
     * @return {@code true} if any of sharedSecret or certificatePath is not {@code null}.
     */
    public final boolean isAppropriateForValidating() {
        return sharedSecret != null || certificatePath != null;
    }

}