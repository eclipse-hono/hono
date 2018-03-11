/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */
package org.eclipse.hono.service.registration;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.JwtHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.vertx.core.Vertx;

/**
 * A utility class for creating and validating JWT tokens asserting the registration status of devices.
 *
 */
public final class RegistrationAssertionHelperImpl extends JwtHelper implements RegistrationAssertionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationAssertionHelperImpl.class);

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
    public static RegistrationAssertionHelper forSigning(final Vertx vertx,
            final SignatureSupportingConfigProperties config) {

        return JwtHelper.forSigning(config, () -> new RegistrationAssertionHelperImpl(vertx));

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
    public static RegistrationAssertionHelper forValidating(final Vertx vertx,
            final SignatureSupportingConfigProperties config) {

        return JwtHelper.forValidating(config, () -> new RegistrationAssertionHelperImpl(vertx));

    }

    /**
     * Creates a helper for creating/validating HmacSHA256 based registration assertions.
     * 
     * @param sharedSecret The shared secret.
     * @param tokenExpiration The number of seconds after which tokens expire.
     * @return The helper.
     * @throws NullPointerException if sharedSecret is {@code null}.
     */
    public static RegistrationAssertionHelper forSharedSecret(final String sharedSecret, final long tokenExpiration) {

        return JwtHelper.forSharedSecret(sharedSecret, tokenExpiration, RegistrationAssertionHelperImpl::new);

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

    @Override
    public long getAssertionLifetime() {
        return getTokenLifetime().getSeconds();
    }
}
