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
package org.eclipse.hono.service.registration;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

import org.eclipse.hono.config.KeyLoader;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.JwtHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.vertx.core.Vertx;

/**
 * A utility class for creating and validating JWT tokens asserting the registration status of devices.
 *
 */
public final class RegistrationAssertionHelperImpl extends JwtHelper implements RegistrationAssertionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationAssertionHelperImpl.class);
    private Vertx vertx;
    private SignatureAlgorithm algorithm;
    private Key key;
    private long tokenExpiration;

    /**
     * Empty default constructor.
     */
    private RegistrationAssertionHelperImpl() {
    }

    private RegistrationAssertionHelperImpl(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Creates a helper for creating registration assertions.
     * 
     * @param vertx The vertx instance to use for accessing the file system.
     * @param config The configuration properties to determine the signing key material from.
     * @return The helper.
     * @throws NullPointerException if any of the params is {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from config.
     */
    public static RegistrationAssertionHelper forSigning(final Vertx vertx, final SignatureSupportingConfigProperties config) {
        Objects.requireNonNull(config);
        if (!config.isAppropriateForCreating()) {
            throw new IllegalArgumentException("configuration does not specify any signing key material");
        } else {
            RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl(vertx);
            result.tokenExpiration = config.getTokenExpiration();
            if (config.getSharedSecret() != null) {
                byte[] secret = getBytes(config.getSharedSecret());
                result.setSharedSecret(secret);
                LOG.info("using shared secret [{} bytes] for signing registration assertions", secret.length);
            } else if (config.getKeyPath() != null) {
                result.setPrivateKey(config.getKeyPath());
                LOG.info("using private key [{}] for signing registration assertions", config.getKeyPath());
            }
            return result;
        }
    }

    /**
     * Creates a helper for validating registration assertions.
     * 
     * @param vertx The vertx instance to use for accessing the file system.
     * @param config The configuration properties to determine the signing key material from.
     * @return The helper.
     * @throws NullPointerException if any of the params is {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from config.
     */
    public static RegistrationAssertionHelper forValidating(final Vertx vertx, final SignatureSupportingConfigProperties config) {
        Objects.requireNonNull(config);
        if (!config.isAppropriateForValidating()) {
            throw new IllegalArgumentException("configuration does not specify any key material for validating signatures");
        } else {
            RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl(vertx);
            if (config.getSharedSecret() != null) {
                byte[] secret = getBytes(config.getSharedSecret());
                result.setSharedSecret(secret);
                LOG.info("using shared secret [{} bytes] for validating registration assertions", secret.length);
            } else if (config.getCertPath() != null) {
                result.setPublicKey(config.getCertPath());
                LOG.info("using public key from certificate [{}] for validating registration assertions", config.getCertPath());
            }
            return result;
        }
    }

    /**
     * Creates a helper for creating/validating HmacSHA256 based registration assertions.
     * 
     * @param sharedSecret The shared secret.
     * @param tokenExpiration The number of minutes after which tokens expire.
     * @return The helper.
     * @throws NullPointerException if sharedSecret is {@code null}.
     */
    public static RegistrationAssertionHelper forSharedSecret(final String sharedSecret, final long tokenExpiration) {
        Objects.requireNonNull(sharedSecret);
        RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl();
        result.setSharedSecret(getBytes(sharedSecret));
        result.tokenExpiration = tokenExpiration;
        return result;
    }

    private static byte[] getBytes(final String secret) {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sets the secret to use for signing tokens asserting the
     * registration status of devices.
     * 
     * @param secret The secret to use.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret is &lt; 32 bytes.
     */
    private void setSharedSecret(final byte[] secret) {
        if (Objects.requireNonNull(secret).length < 32) {
            throw new IllegalArgumentException("shared secret must be at least 32 bytes");
        }
        this.algorithm = SignatureAlgorithm.HS256;
        this.key = new SecretKeySpec(secret, SignatureAlgorithm.HS256.getJcaName());
    }

    /**
     * Sets the path to a PKCS8 PEM file containing the RSA private key to use for signing tokens
     * asserting the registration status of devices.
     * 
     * @param keyPath The absolute path to the file.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    private void setPrivateKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        this.algorithm = SignatureAlgorithm.RS256;
        this.key = KeyLoader.fromFiles(vertx, keyPath, null).getPrivateKey();
        if (key == null) {
            throw new IllegalArgumentException("cannot load private key");
        }
    }

    /**
     * Sets the path to a PEM file containing a certificate holding a public key to use for validating the
     * signature of tokens asserting the registration status of devices.
     * 
     * @param keyPath The absolute path to the file.
     * @throws NullPointerException if the path is {@code null}.
     * @throws IllegalArgumentException if the key cannot be read from the file.
     */
    private void setPublicKey(final String keyPath) {
        Objects.requireNonNull(keyPath);
        this.algorithm = SignatureAlgorithm.RS256;
        this.key = KeyLoader.fromFiles(vertx, null, keyPath).getPublicKey();
        if (key == null) {
            throw new IllegalArgumentException("cannot load private key");
        }
    }

    @Override
    public String getAssertion(final String tenantId, final String deviceId) {

        if (algorithm == null) {
            throw new IllegalStateException("no algorithm set");
        }

        return Jwts.builder().signWith(algorithm, key)
                .setSubject(deviceId)
                .claim("ten", tenantId)
                .setExpiration(Date.from(Instant.now().plus(tokenExpiration, ChronoUnit.MINUTES)))
                .compact();
    }

    @Override
    public boolean isValid(final String token, final String tenantId, final String deviceId) {

        try {
            Jwts.parser()
                .setSigningKey(key)
                .requireSubject(Objects.requireNonNull(deviceId))
                .require("ten", Objects.requireNonNull(tenantId))
                .setAllowedClockSkewSeconds(10)
                .parse(token);
            return true;
        } catch (JwtException e) {
            // token is invalid for some reason
            LOG.debug("failed to validate token", e);
            return false;
        }
    }
}
