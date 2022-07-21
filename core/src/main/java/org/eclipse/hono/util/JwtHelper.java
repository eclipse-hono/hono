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
package org.eclipse.hono.util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import javax.crypto.spec.SecretKeySpec;

import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Vertx;

/**
 * A utility class for generating JWT tokens asserting the registration status of devices.
 *
 */
public abstract class JwtHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JwtHelper.class);

    /**
     * The secret key used for signing.
     */
    protected Key key;
    /**
     * The lifetime of created tokens.
     */
    protected Duration tokenLifetime;

    private final Vertx vertx;

    /**
     * Creates a new helper for a vertx instance.
     *
     * @param vertx The vertx instance to use for loading key material from the file system.
     */
    protected JwtHelper(final Vertx vertx) {
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
        } else if (!(key instanceof ECKey) && !(key instanceof RSAKey)) {
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
        } else if (!(key instanceof ECKey) && !(key instanceof RSAKey)) {
            throw new IllegalArgumentException("unsupported public key type: " + key.getClass());
        }
    }

    /**
     * Gets the duration being used for calculating the <em>exp</em> claim of tokens created by this class.
     * <p>
     * Clients should always check if a token is expired before using any information contained in the token.
     *
     * @return The duration.
     */
    public final Duration getTokenLifetime() {
        return tokenLifetime;
    }

    /**
     * Creates a helper that can be used for creating and verifying signatures of JWTs.
     *
     * @param <T> The type of helper to create.
     * @param sharedSecret The shared secret to use for signatures.
     * @param tokenExpiration The number of seconds after which the tokens created by this
     *                        helper should be considered expired.
     * @param instanceSupplier The supplier to invoke for creating the new helper instance.
     * @return The newly created helper.
     */
    protected static <T extends JwtHelper> T forSharedSecret(final String sharedSecret, final long tokenExpiration,
            final Supplier<T> instanceSupplier) {

        Objects.requireNonNull(sharedSecret);
        Objects.requireNonNull(instanceSupplier);

        final T result = instanceSupplier.get();
        result.setSharedSecret(getBytes(sharedSecret));
        result.tokenLifetime = Duration.ofSeconds(tokenExpiration);
        return result;
    }

    /**
     * Creates a helper that can be used for creating signed JWTs.
     *
     * @param <T> The type of helper to create.
     * @param config The key material to use for signing.
     * @param instanceSupplier The supplier to invoke for creating the new helper instance.
     * @return The newly created helper.
     * @throws IllegalArgumentException if the given configuration does not contain key material required
     *                                  for signing tokens.
     */
    protected static <T extends JwtHelper> T forSigning(final SignatureSupportingConfigProperties config,
            final Supplier<T> instanceSupplier) {

        Objects.requireNonNull(config);
        Objects.requireNonNull(instanceSupplier);

        if (!config.isAppropriateForCreating()) {
            throw new IllegalArgumentException("configuration does not specify any signing tokens");
        } else {
            final T result = instanceSupplier.get();
            result.tokenLifetime = Duration.ofSeconds(config.getTokenExpiration());
            LOG.info("using token lifetime of {} seconds", result.tokenLifetime.getSeconds());
            if (config.getSharedSecret() != null) {
                final byte[] secret = getBytes(config.getSharedSecret());
                result.setSharedSecret(secret);
                LOG.info("using shared secret [{} bytes] for signing tokens", secret.length);
            } else if (config.getKeyPath() != null) {
                result.setPrivateKey(config.getKeyPath());
                LOG.info("using private key [{}] for signing tokens", config.getKeyPath());
            }
            return result;
        }
    }

    /**
     * Creates a helper that can be used for verifying signatures of JWTs.
     *
     * @param <T> The type of helper to create.
     * @param config The key material to use for verifying signatures.
     * @param instanceSupplier The supplier to invoke for creating the new helper instance.
     * @return The newly created helper.
     * @throws IllegalArgumentException if the given configuration does not contain key material required
     *                                  for validating a token's signature.
     */
    protected static <T extends JwtHelper> T forValidating(final SignatureSupportingConfigProperties config,
            final Supplier<T> instanceSupplier) {

        Objects.requireNonNull(config);
        Objects.requireNonNull(instanceSupplier);

        if (!config.isAppropriateForValidating()) {
            throw new IllegalArgumentException(
                    "configuration does not specify any key material for validating tokens");
        } else {
            final T result = instanceSupplier.get();
            if (config.getSharedSecret() != null) {
                final byte[] secret = getBytes(config.getSharedSecret());
                result.setSharedSecret(secret);
                LOG.info("using shared secret [{} bytes] for validating tokens", secret.length);
            } else if (config.getCertPath() != null) {
                result.setPublicKey(config.getCertPath());
                LOG.info("using public key from certificate [{}] for validating tokens", config.getCertPath());
            }
            return result;
        }
    }
}
