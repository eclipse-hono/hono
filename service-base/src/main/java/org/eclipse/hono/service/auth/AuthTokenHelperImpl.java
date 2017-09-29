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

package org.eclipse.hono.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.JwtHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Vertx;


/**
 * A helper for creating and validating JSON Web Tokens containing user claims.
 *
 */
public class AuthTokenHelperImpl extends JwtHelper implements AuthTokenHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AuthTokenHelperImpl.class);

    private AuthTokenHelperImpl() {
        this(null);
    }

    private AuthTokenHelperImpl(final Vertx vertx) {
        super(vertx);
    }

    /**
     * Creates a helper for creating tokens.
     * 
     * @param vertx The vertx instance to use for accessing the file system.
     * @param config The configuration properties to determine the signing key material from.
     * @return The helper.
     * @throws NullPointerException if any of the params is {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from config.
     */
    public static AuthTokenHelper forSigning(final Vertx vertx, final SignatureSupportingConfigProperties config) {
        Objects.requireNonNull(config);
        if (!config.isAppropriateForCreating()) {
            throw new IllegalArgumentException("configuration does not specify any signing key material");
        } else {
            AuthTokenHelperImpl result = new AuthTokenHelperImpl(vertx);
            result.tokenLifetime = Duration.ofSeconds(config.getTokenExpiration());
            result.setValidationCacheMaxSize(config.getValidationCacheMaxSize());
            if (config.getSharedSecret() != null) {
                byte[] secret = getBytes(config.getSharedSecret());
                result.setSharedSecret(secret);
                LOG.info("using shared secret [{} bytes] for signing JWTs", secret.length);
            } else if (config.getKeyPath() != null) {
                result.setPrivateKey(config.getKeyPath());
                LOG.info("using private key [{}] for signing JWTs", config.getKeyPath());
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
    public static AuthTokenHelper forValidating(final Vertx vertx, final SignatureSupportingConfigProperties config) {
        Objects.requireNonNull(config);
        if (!config.isAppropriateForValidating()) {
            throw new IllegalArgumentException("configuration does not specify any key material for validating signatures");
        } else {
            AuthTokenHelperImpl result = new AuthTokenHelperImpl(vertx);
            result.setValidationCacheMaxSize(config.getValidationCacheMaxSize());
            if (config.getSharedSecret() != null) {
                byte[] secret = getBytes(config.getSharedSecret());
                result.setSharedSecret(secret);
                LOG.info("using shared secret [{} bytes] for validating JWT signatures", secret.length);
            } else if (config.getCertPath() != null) {
                result.setPublicKey(config.getCertPath());
                LOG.info("using public key from certificate [{}] for validating JWT signatures", config.getCertPath());
            }
            return result;
        }
    }

    /**
     * Creates a helper for creating/validating HmacSHA256 based registration assertions.
     * 
     * @param sharedSecret The shared secret.
     * @param tokenExpirationSeconds The number of seconds after which tokens created expire.
     * @return The helper.
     * @throws NullPointerException if sharedSecret is {@code null}.
     */
    public static AuthTokenHelper forSharedSecret(final String sharedSecret, final long tokenExpirationSeconds) {
        Objects.requireNonNull(sharedSecret);
        AuthTokenHelperImpl result = new AuthTokenHelperImpl();
        result.setSharedSecret(getBytes(sharedSecret));
        result.tokenLifetime = Duration.ofSeconds(tokenExpirationSeconds);
        return result;
    }

    @Override
    public String createToken(final String authorizationId, final Authorities authorities) {

        JwtBuilder builder = Jwts.builder()
                .signWith(algorithm, key)
                .setIssuer("Hono")
                .setSubject(Objects.requireNonNull(authorizationId))
                .setExpiration(Date.from(Instant.now().plus(tokenLifetime)));
        if (authorities != null) {
            authorities.asMap().forEach((key, value) -> {
                builder.claim(key, value);
            });
        }
        return builder.compact();
    }

    @Override
    public Jws<Claims> expand(final String token) {

        Objects.requireNonNull(token);
        return Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token);
    }
}
