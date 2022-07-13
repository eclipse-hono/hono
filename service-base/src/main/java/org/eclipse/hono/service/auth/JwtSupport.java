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
import java.security.Key;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

import org.eclipse.hono.config.KeyLoader;

import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Vertx;

/**
 * A base class for keeping track of key material for creating and validating signatures.
 *
 */
abstract class JwtSupport {

    /**
     * The signature algorithm used for signing.
     */
    protected SignatureAlgorithm algorithm;
    /**
     * The secret key used for signing.
     */
    protected Key key;

    private final Vertx vertx;

    /**
     * Creates a new helper for a vertx instance.
     *
     * @param vertx The vertx instance to use for loading key material from the file system.
     */
    protected JwtSupport(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Gets the bytes representing the UTF8 encoding of a secret.
     *
     * @param secret The string to get the bytes for.
     * @return The bytes.
     */
    protected static final byte[] getBytes(final String secret) {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sets the secret to use for signing tokens asserting the registration status of devices.
     *
     * @param secret The secret to use.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret is &lt; 32 bytes.
     */
    protected final void setSharedSecret(final byte[] secret) {
        if (Objects.requireNonNull(secret).length < 32) {
            throw new IllegalArgumentException("shared secret must be at least 32 bytes");
        }
        this.algorithm = SignatureAlgorithm.HS256;
        this.key = new SecretKeySpec(secret, SignatureAlgorithm.HS256.getJcaName());
    }

    /**
     * Sets the path to a PKCS8 PEM file containing the RSA private key to use for signing tokens asserting the
     * registration status of devices.
     *
     * @param keyPath The absolute path to the file.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    protected final void setPrivateKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        key = KeyLoader.fromFiles(vertx, keyPath, null).getPrivateKey();
        if (key == null) {
            throw new IllegalArgumentException("cannot load private key: " + keyPath);
        } else if (key instanceof ECKey) {
            algorithm = SignatureAlgorithm.ES256;
        } else if (key instanceof RSAKey) {
            algorithm = SignatureAlgorithm.RS256;
        } else {
            throw new IllegalArgumentException("unsupported private key type: " + key.getClass());
        }
    }

    /**
     * Sets the path to a PEM file containing a certificate holding a public key to use for validating the signature of
     * tokens asserting the registration status of devices.
     *
     * @param keyPath The absolute path to the file.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    protected final void setPublicKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        key = KeyLoader.fromFiles(vertx, null, keyPath).getPublicKey();
        if (key == null) {
            throw new IllegalArgumentException("cannot load public key: " + keyPath);
        } else if (key instanceof ECKey) {
            algorithm = SignatureAlgorithm.ES256;
        } else if (key instanceof RSAKey) {
            algorithm = SignatureAlgorithm.RS256;
        } else {
            throw new IllegalArgumentException("unsupported public key type: " + key.getClass());
        }
    }
}
