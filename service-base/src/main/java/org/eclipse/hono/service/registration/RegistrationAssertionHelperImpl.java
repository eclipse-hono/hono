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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.JwtHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Vertx;

/**
 * A utility class for creating and validating JWT tokens asserting the registration status of devices.
 *
 */
public final class RegistrationAssertionHelperImpl extends JwtHelper implements RegistrationAssertionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationAssertionHelperImpl.class);

    private LoadingCache<RegistrationAssertionCacheKey, Instant> validationCache;

    private RegistrationAssertionHelperImpl() {
        this(null);
    }

    private RegistrationAssertionHelperImpl(final Vertx vertx) {
        super(vertx);
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
            result.tokenLifetime = Duration.ofSeconds(config.getTokenExpiration());
            result.setValidationCacheMaxSize(config.getValidationCacheMaxSize());
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
            result.tokenLifetime = Duration.ofSeconds(config.getTokenExpiration());
            result.setValidationCacheMaxSize(config.getValidationCacheMaxSize());
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
     * @param validationCacheMaxSize max size for validation cache holding the registration assertions requests
     * @return The helper.
     * @throws NullPointerException if sharedSecret is {@code null}.
     */
    public static RegistrationAssertionHelper forSharedSecret(final String sharedSecret, final long tokenExpiration, final long validationCacheMaxSize) {
        Objects.requireNonNull(sharedSecret);
        RegistrationAssertionHelperImpl result = new RegistrationAssertionHelperImpl();
        result.setSharedSecret(getBytes(sharedSecret));
        result.tokenLifetime = Duration.ofMinutes(tokenExpiration);
        result.setValidationCacheMaxSize(validationCacheMaxSize);
        return result;
    }

    @Override
    public String getAssertion(final String tenantId, final String deviceId) {

        if (algorithm == null) {
            throw new IllegalStateException("no algorithm set");
        }

        return Jwts.builder().signWith(algorithm, key)
                .setSubject(deviceId)
                .claim("ten", tenantId)
                .setExpiration(Date.from(Instant.now().plus(tokenLifetime)))
                .compact();
    }

    @Override
    public boolean isValid(final String token, final String tenantId, final String deviceId) {

        if (validationCache == null) {
            initValidationCache();
        }

        try {
            RegistrationAssertionCacheKey cacheKey = new RegistrationAssertionCacheKey(token, tenantId, deviceId);
            Instant expirationTime = validationCache.get(cacheKey);
            return isNotExpired(expirationTime);
        } catch (ExecutionException | UncheckedExecutionException | JwtException e) {
            // token is invalid for some reason
            LOG.debug("failed to validate token", e);
            return false;
        }
    }

    private boolean isNotExpired(final Instant expirationTime) {
        Instant now = Instant.now().minus(Duration.ofSeconds(allowedClockSkew));

        if (expirationTime.isBefore(now)) {
            LOG.debug("token is already expired");
            return false;
        } else {
            return true;
        }
    }

    private void initValidationCache() {

        /*
         * Using LoadingCache, which works with "if cached, return; otherwise create, cache and return" pattern
         */
        validationCache = CacheBuilder.newBuilder()
            .maximumSize(validationCacheMaxSize)
            .expireAfterWrite(tokenLifetime.getSeconds(), TimeUnit.SECONDS)
            .build(new CacheLoader<RegistrationAssertionCacheKey, Instant>() {
                @Override
                public Instant load(RegistrationAssertionCacheKey cacheKey) throws Exception {

                    // validate token
                    Jws<Claims> claimsJws = Jwts.parser()
                        .setSigningKey(key)
                        .requireSubject(Objects.requireNonNull(cacheKey.getDeviceId()))
                        .require("ten", Objects.requireNonNull(cacheKey.getTenantId()))
                        .setAllowedClockSkewSeconds(allowedClockSkew)
                        .parseClaimsJws(cacheKey.getToken());

                    // save expiration time
                    return claimsJws.getBody().getExpiration().toInstant();
                }
            });
    }
}
