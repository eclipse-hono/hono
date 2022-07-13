/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.auth;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Common properties required for creating/validating cryptographic signatures.
 *
 */
public class SignatureSupportingConfigProperties {

    private String sharedSecret = null;
    private String keyPath = null;
    private long tokenExpiration = 600L;
    private String certPath = null;

    /**
     * Creates new properties using default values.
     */
    public SignatureSupportingConfigProperties() {
        super();
    }

    /**
     * Creates a new instance from existing options.
     *
     * @param options The options to copy.
     */
    public SignatureSupportingConfigProperties(final SignatureSupportingOptions options) {
        super();
        this.certPath = options.certPath().orElse(null);
        this.keyPath = options.keyPath().orElse(null);
        options.sharedSecret().ifPresent(this::setSharedSecret);
        setTokenExpiration(options.tokenExpiration());
    }

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
        return tokenExpiration;
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
        this.tokenExpiration = seconds;
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
        this.certPath = Objects.requireNonNull(certPath);
    }

    /**
     * Gets the path to the X.509 certificate containing the public key to be used
     * for validating RSA based signatures.
     *
     * @return The path to the file or {@code null} if not set.
     */
    public final String getCertPath() {
        return certPath;
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
        return sharedSecret != null || certPath != null;
    }
}
