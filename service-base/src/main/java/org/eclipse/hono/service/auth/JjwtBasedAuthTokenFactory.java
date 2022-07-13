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

package org.eclipse.hono.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import org.eclipse.hono.auth.Authorities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.Vertx;

/**
 * A JJWT based factory for creating signed JSON Web Tokens containing user claims.
 *
 */
@RegisterForReflection(targets = {
                        io.jsonwebtoken.impl.DefaultJwtBuilder.class,
                        io.jsonwebtoken.jackson.io.JacksonSerializer.class,
                        io.jsonwebtoken.impl.compression.GzipCompressionCodec.class
})
public class JjwtBasedAuthTokenFactory extends JwtSupport implements AuthTokenFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JjwtBasedAuthTokenFactory.class);

    private final Duration tokenLifetime;

    /**
     * Creates a helper for creating tokens.
     *
     * @param vertx The vertx instance to use for accessing the file system.
     * @param config The configuration properties to determine the signing key material from.
     * @throws NullPointerException if any of the params is {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from config.
     */
    public JjwtBasedAuthTokenFactory(final Vertx vertx, final SignatureSupportingConfigProperties config) {

        super(vertx);
        Objects.requireNonNull(config);

        if (config.getSharedSecret() != null) {
            final var bytes = getBytes(config.getSharedSecret());
            setSharedSecret(bytes);
            LOG.info("using shared secret [{} bytes] for signing/validating tokens", bytes.length);
        } else if (config.getKeyPath() != null) {
            setPrivateKey(config.getKeyPath());
            LOG.info("using private key [{}] for signing tokens", config.getKeyPath());
        } else {
            throw new IllegalArgumentException(
                    "configuration does not specify any key material for signing tokens");
        }
        tokenLifetime = Duration.ofSeconds(config.getTokenExpiration());
        LOG.info("using token lifetime of {} seconds", tokenLifetime.getSeconds());
    }

    @Override
    public Duration getTokenLifetime() {
        return tokenLifetime;
    }

    @Override
    public String createToken(final String authorizationId, final Authorities authorities) {

        final JwtBuilder builder = Jwts.builder()
                .signWith(key, algorithm)
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
}
