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

package org.eclipse.hono.service.auth;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.JwtHelper;

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

        return JwtHelper.forSigning(config, () -> new AuthTokenHelperImpl(vertx));

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

        return JwtHelper.forValidating(config, () -> new AuthTokenHelperImpl(vertx));

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

        return JwtHelper.forSharedSecret(sharedSecret, tokenExpirationSeconds, AuthTokenHelperImpl::new);

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
