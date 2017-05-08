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

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.SigningKeyResolverAdapter;

/**
 * A utility class for generating JWT tokens asserting the registration status of devices.
 *
 */
public abstract class JwtHelper {

    private static final Key DUMMY_KEY = new SecretKeySpec(new byte[]{ 0x00,  0x01 }, SignatureAlgorithm.HS256.getJcaName());
    protected final SignatureAlgorithm algorithm;
    protected final Key signingKey;

    /**
     * Creates a helper for creating registration assertions.
     * 
     * @param algorithm The algorithm to use for signing assertions.
     * @param signingKey The key to use for validating the assertions' signature.
     * @throws NullPointerException if algorithm or key are {@code null}.
     */
    public JwtHelper(final SignatureAlgorithm algorithm, final Key signingKey) {
        this.algorithm = algorithm;
        this.signingKey = signingKey;
    }

    /**
     * Creates a helper for creating/validating <em>HS256</em> based registration assertions.
     * 
     * @param secret The secret to use for validating the assertion's signature.
     * @throws NullPointerException if secret is {@code null}.
     */
    public JwtHelper(final String secret) {
        this(SignatureAlgorithm.HS256, new SecretKeySpec(
                Objects.requireNonNull(secret).getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName()));
    }

    /**
     * Creates a helper for validating registration assertions.
     * 
     * @param signingKey The key to use for validating the assertion's signature.
     * @throws NullPointerException if key is {@code null}.
     */
    public JwtHelper(final Key signingKey) {
        this(null, Objects.requireNonNull(signingKey));
    }

    /**
     * Checks if a token is expired.
     * 
     * @param token The token to check.
     * @param allowedClockSkewSeconds The allowed clock skew in seconds.
     * @return {@code true} if the token is expired (including allowed skew).
     */
    public static final boolean isExpired(final String token, final int allowedClockSkewSeconds) {

        if (token == null) {
            throw new NullPointerException("token must not be null");
        }

        final AtomicBoolean result = new AtomicBoolean(false);

        try {
            Jwts.parser().setSigningKeyResolver(new SigningKeyResolverAdapter(){
                @SuppressWarnings("rawtypes")
                @Override
                public Key resolveSigningKey(JwsHeader header, Claims claims) {
                    Date exp = claims.getExpiration();
                    if (exp != null) {
                        result.set(exp.before(Date.from(Instant.now().minus(Duration.ofSeconds(allowedClockSkewSeconds)))));
                    }
                    return DUMMY_KEY;
                }
            }).parse(token);
        } catch (SignatureException e) {
            // expected since we do not know the signing key
        }
        return result.get();
    }
}
