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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.crypto.SecretKey;

import org.eclipse.hono.config.KeyLoader;

import com.google.common.hash.Hashing;

import io.vertx.core.Vertx;

/**
 * A base class for keeping track of key material for creating and validating signatures.
 *
 */
abstract class JwtSupport {

    /**
     * The Vert.x instance to run on.
     */
    protected final Vertx vertx;

    private final Map<String, Key> signingKeys = new HashMap<>(5);
    private final Map<String, Key> validatingKeys = new HashMap<>(5);

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

    private String createKeyId(final byte[] encodedKey) {
        return Hashing.sha256().hashBytes(encodedKey).toString();
    }

    /**
     * Adds a secret to use for signing tokens.
     *
     * @param secretKey The secret to use.
     * @return The identifier that has been assigned to the key.
     * @throws NullPointerException if key is {@code null}.
     */
    protected final String addSecretKey(final SecretKey secretKey) {
        Objects.requireNonNull(secretKey);
        final var id = createKeyId(secretKey.getEncoded());
        this.signingKeys.put(id, secretKey);
        this.validatingKeys.put(id, secretKey);
        return id;
    }

    /**
     * Adds a key pair to use for signing/validating tokens.
     *
     * @param keyPath The absolute path to the PKCS#8 PEM file that contains the private key.
     * @param certPath The absolute path to the PKCS#1 PEM file that contains the X.509 certificate containing the public key.
     * @return The identifier that has been assigned to the key pair.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if any of the files can not be read.
     */
    protected final String addPrivateKey(final String keyPath, final String certPath) {
        Objects.requireNonNull(keyPath);
        Objects.requireNonNull(certPath);
        final var keys = KeyLoader.fromFiles(vertx, keyPath, certPath);
        return addPrivateKey(keys.getPrivateKey(), keys.getPublicKey());
    }

    /**
     * Adds a key pair to use for signing/validating tokens.
     *
     * @param privateKey The key to use for signing.
     * @param publicKey The key to use for validating.
     * @return The identifier that has been assigned to the key pair.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final String addPrivateKey(final PrivateKey privateKey, final PublicKey publicKey) {
        Objects.requireNonNull(privateKey);
        Objects.requireNonNull(publicKey);
        final var keyId = createKeyId(publicKey.getEncoded());
        addPrivateKey(keyId, privateKey, publicKey);
        return keyId;
    }

    /**
     * Adds a key pair to use for signing/validating tokens.
     *
     * @param keyId The key's identifier.
     * @param privateKey The key to use for signing.
     * @param publicKey The key to use for validating.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final void addPrivateKey(final String keyId, final PrivateKey privateKey, final PublicKey publicKey) {
        Objects.requireNonNull(keyId);
        Objects.requireNonNull(privateKey);
        Objects.requireNonNull(publicKey);
        this.signingKeys.put(keyId, privateKey);
        this.validatingKeys.put(keyId, publicKey);
    }

    /**
     * Sets the public key to use for verifying the signatures of tokens.
     *
     * @param keyPath The absolute path to a PEM file containing a PKIX certificate holding a public key.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    protected final void setPublicKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        final var publicKey = KeyLoader.fromFiles(vertx, null, keyPath).getPublicKey();
        if (publicKey == null) {
            throw new IllegalArgumentException("cannot load public key: " + keyPath);
        } else {
            setValidatingKeys(Map.of(createKeyId(publicKey.getEncoded()), publicKey));
        }
    }

    /**
     * Sets the public keys to use for verifying the signatures of tokens.
     *
     * @param keys (key ID, key) tuples.
     * @throws NullPointerException if keys is {@code null}.
     */
    protected final void setValidatingKeys(final Map<String, PublicKey> keys) {
        Objects.requireNonNull(keys);
        this.validatingKeys.clear();
        this.validatingKeys.putAll(keys);
    }

    /**
     * Gets the validating key.
     *
     * @return The key.
     * @throws IllegalStateException if no key or more than one key is registered.
     */
    protected final Key getValidatingKey() {
        if (validatingKeys.size() != 1) {
            throw new IllegalStateException("more than one validating key is registered");
        }
        return validatingKeys.values().iterator().next();
    }

    /**
     * Gets a validating key by its identifier.
     *
     * @param keyId The identifier.
     * @return The key or {@code null} if no key is registered for the given identifier.
     * @throws NullPointerException if key ID is {@code null}.
     */
    protected final Key getValidatingKey(final String keyId) {
        Objects.requireNonNull(keyId);
        return validatingKeys.get(keyId);
    }

    /**
     * Gets all registered keys for validating signatures.
     *
     * @return An unmodifiable view on the set of (key ID, key) tuples.
     */
    protected final Set<Map.Entry<String, Key>> getValidatingKeys() {
        return Collections.unmodifiableSet(validatingKeys.entrySet());
    }

    /**
     * Checks if at least one key for validating signatures has been registered.
     *
     * @return {@code true} if validating keys map is not empty.
     */
    public final boolean hasValidatingKey() {
        return !validatingKeys.isEmpty();
    }

    /**
     * Gets a signing key by its identifier.
     *
     * @param keyId The identifier.
     * @return The key or {@code null} if no key is registered for the given identifier.
     * @throws NullPointerException if key ID is {@code null}.
     */
    protected final Key getSigningKey(final String keyId) {
        Objects.requireNonNull(keyId);
        return signingKeys.get(keyId);
    }
}
