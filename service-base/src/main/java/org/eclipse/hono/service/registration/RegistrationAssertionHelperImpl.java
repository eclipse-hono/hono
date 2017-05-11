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
     */
    public static RegistrationAssertionHelper forSigning(final Vertx vertx, final SignatureSupportingConfigProperties config) {
        Objects.requireNonNull(config);
        if (config.getSharedSecret() == null && config.getKeyPath() == null) {
            throw new IllegalArgumentException("configuration does not specify any signing key material");
        } else {
            RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl(vertx);
            result.tokenExpiration = config.getTokenExpiration();
            if (config.getSharedSecret() != null) {
                result.setSharedSecret(config.getSharedSecret());
            } else if (config.getKeyPath() != null) {
                result.setPrivateKey(config.getKeyPath());
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
     */
    public static final RegistrationAssertionHelper forValidating(final Vertx vertx, final SignatureSupportingConfigProperties config) {
        Objects.requireNonNull(config);
        if (config.getSharedSecret() == null && config.getCertificatePath() == null) {
            throw new IllegalArgumentException("configuration does not specify any key material for validating signatures");
        } else {
            RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl(vertx);
            if (config.getSharedSecret() != null) {
                result.setSharedSecret(config.getSharedSecret());
            } else if (config.getCertificatePath() != null) {
                result.setPublicKey(config.getCertificatePath());
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
    public static final RegistrationAssertionHelper forSharedSecret(final String sharedSecret, final long tokenExpiration) {
        Objects.requireNonNull(sharedSecret);
        RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl();
        result.setSharedSecret(sharedSecret);
        result.tokenExpiration = tokenExpiration;
        return result;
    }

    /**
     * Sets the secret to use for signing tokens asserting the
     * registration status of devices.
     * 
     * @param secret The secret to use.
     * @throws NullPointerException if secret is {@code null}.
     */
    private void setSharedSecret(final String secret) {
        this.algorithm = SignatureAlgorithm.HS256;
        this.key = new SecretKeySpec(
                Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
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
