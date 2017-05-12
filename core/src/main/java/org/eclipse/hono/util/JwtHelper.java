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
package org.eclipse.hono.util;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SigningKeyResolverAdapter;

/**
 * A utility class for generating JWT tokens asserting the registration status of devices.
 *
 */
public abstract class JwtHelper {

    private static final Key DUMMY_KEY = new SecretKeySpec(new byte[]{ 0x00,  0x01 }, SignatureAlgorithm.HS256.getJcaName());

    /**
     * Checks if a token is expired.
     * 
     * @param token The token to check.
     * @param allowedClockSkewSeconds The allowed clock skew in seconds.
     * @return {@code true} if the token is expired according to the current system time (including allowed skew).
     */
    public static final boolean isExpired(final String token, final int allowedClockSkewSeconds) {
        Instant now = Instant.now().minus(Duration.ofSeconds(allowedClockSkewSeconds));
        return isExpired(token, now);
    }

    /**
     * Checks if a token is expired.
     * 
     * @param token The token to check.
     * @param now The instant of time the token's expiration time should be checked against.
     * @return {@code true} if the token is expired according to the given instant of time.
     * @throws NullPointerException if the token is {@code null}.
     * @throws IllegalArgumentException if the given token contains no <em>exp</em> claim.
     */
    public static final boolean isExpired(final String token, final Instant now) {

        if (token == null) {
            throw new NullPointerException("token must not be null");
        } else {
            Date exp = getExpiration(token);
            return exp.before(Date.from(now));
        }
    }

    /**
     * Gets the value of the <em>exp</em> claim of a JWT.
     * 
     * @param token The token.
     * @return The expiration.
     * @throws NullPointerException if the token is {@code null}.
     * @throws IllegalArgumentException if the given token contains no <em>exp</em> claim.
     */
    public static final Date getExpiration(final String token) {

        if (token == null) {
            throw new NullPointerException("token must not be null");
        }

        final AtomicReference<Date> result = new AtomicReference<Date>();

        try {
            Jwts.parser().setSigningKeyResolver(new SigningKeyResolverAdapter(){
                @SuppressWarnings("rawtypes")
                @Override
                public Key resolveSigningKey(JwsHeader header, Claims claims) {
                    Date exp = claims.getExpiration();
                    if (exp != null) {
                        result.set(exp);
                    }
                    return DUMMY_KEY;
                }
            }).parse(token);
        } catch (JwtException e) {
            // expected since we do not know the signing key
        }

        if (result.get() == null) {
            throw new IllegalArgumentException("token contains no exp claim");
        } else {
            return result.get();
        }
    }
}
