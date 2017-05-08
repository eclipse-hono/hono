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

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;

import org.eclipse.hono.util.JwtHelper;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * A utility class for creating and validating JWT tokens asserting the registration status of devices.
 *
 */
public final class RegistrationAssertionHelper extends JwtHelper {

    /**
     * Creates a helper for validating registration assertions.
     * 
     * @param signingKey The key to use for validating the assertion's signature.
     * @throws NullPointerException if key is {@code null}.
     */
    public RegistrationAssertionHelper(final Key signingKey) {
        super(signingKey);
    }

    /**
     * Creates a helper for creating/validating registration assertions.
     * 
     * @param algorithm The algorithm to use for signing assertions.
     * @param signingKey The key to use for validating the assertions' signature.
     * @throws NullPointerException if algorithm or key are {@code null}.
     */
    public RegistrationAssertionHelper(final SignatureAlgorithm algorithm, final Key signingKey) {
        super(algorithm, signingKey);
    }

    /**
     * Creates a helper for creating/validating <em>HS256</em> based registration assertions.
     * 
     * @param secret The secret to use for validating the assertion's signature.
     * @throws NullPointerException if secret is {@code null}.
     */
    public RegistrationAssertionHelper(final String secret) {
        super(secret);
    }

    /**
     * Creates a signed assertion.
     * 
     * @param tenantId The tenant to which the device belongs.
     * @param deviceId The device that is subject of the assertion.
     * @param expirationInMinutes The number of minutes the assertion should be valid for.
     * @return The assertion.
     */
    public String getAssertion(final String tenantId, final String deviceId, final long expirationInMinutes) {

        if (algorithm == null) {
            throw new IllegalStateException("no algorithm set");
        }

        return Jwts.builder().signWith(algorithm, signingKey)
                .setSubject(deviceId)
                .claim("ten", tenantId)
                .setExpiration(new Date(Instant.now().plus(expirationInMinutes, ChronoUnit.MINUTES).toEpochMilli()))
                .compact();
    }

    /**
     * Checks if a given token asserts a particular device's registration status.
     * 
     * @param token The token representing the asserted status.
     * @param tenantId The tenant that the device is expected to belong to.
     * @param deviceId The device that is expected to be the subject of the assertion.
     * @return {@code true} if the token has not expired and contains the following claims:
     * <ul>
     * <li>a <em>sub</em> claim with value device ID</li>
     * <li>a <em>ten</em> claim with value tenant ID</li>
     * </ul>
     */
    public boolean isValid(final String token, final String tenantId, final String deviceId) {

        try {
            Jwts.parser()
                .setSigningKey(signingKey)
                .requireSubject(Objects.requireNonNull(deviceId))
                .require("ten", Objects.requireNonNull(tenantId))
                .parse(token);
            return true;
        } catch (JwtException e) {
            // token is invalid for some reason
            return false;
        }
    }
}
