/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.eclipse.hono.auth.Authorities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.Vertx;

/**
 * A JJWT based factory for creating signed JSON Web Tokens containing user claims.
 *
 */
@RegisterForReflection(targets = {
                        io.jsonwebtoken.impl.DefaultClaimsBuilder.class,
                        io.jsonwebtoken.impl.DefaultJwtBuilder.class,
                        io.jsonwebtoken.impl.DefaultJwtHeaderBuilder.class,
                        io.jsonwebtoken.impl.DefaultJwtParserBuilder.class,
                        io.jsonwebtoken.impl.compression.DeflateCompressionAlgorithm.class,
                        io.jsonwebtoken.impl.compression.GzipCompressionAlgorithm.class,
                        io.jsonwebtoken.impl.io.StandardCompressionAlgorithms.class,
                        io.jsonwebtoken.impl.security.DefaultDynamicJwkBuilder.class,
                        io.jsonwebtoken.impl.security.DefaultJwkParserBuilder.class,
                        io.jsonwebtoken.impl.security.DefaultJwkSetBuilder.class,
                        io.jsonwebtoken.impl.security.DefaultJwkSetParserBuilder.class,
                        io.jsonwebtoken.impl.security.DefaultKeyOperationBuilder.class,
                        io.jsonwebtoken.impl.security.DefaultKeyOperationPolicyBuilder.class,
                        io.jsonwebtoken.impl.security.JwksBridge.class,
                        io.jsonwebtoken.impl.security.StandardCurves.class,
                        io.jsonwebtoken.impl.security.StandardEncryptionAlgorithms.class,
                        io.jsonwebtoken.impl.security.StandardHashAlgorithms.class,
                        io.jsonwebtoken.impl.security.StandardKeyAlgorithms.class,
                        io.jsonwebtoken.impl.security.StandardKeyOperations.class,
                        io.jsonwebtoken.impl.security.StandardSecureDigestAlgorithms.class,
                        io.jsonwebtoken.jackson.io.JacksonDeserializer.class,
                        io.jsonwebtoken.jackson.io.JacksonSerializer.class
})
public final class JjwtBasedAuthTokenFactory extends JwtSupport implements AuthTokenFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JjwtBasedAuthTokenFactory.class);

    private final JwkSet jwkSet;
    private final SignatureSupportingConfigProperties config;
    private final Duration tokenLifetime;
    private final String signingKeyId;

    /**
     * Creates a factory for configuration properties.
     * <p>
     * The factory will use the strongest hash algorithm that the key material is compatible with.
     *
     * @param vertx The vertx instance to use for accessing the file system.
     * @param config The configuration properties to determine the signing key material from.
     * @throws NullPointerException if any of the params is {@code null}.
     * @throws IllegalArgumentException if the key material cannot be determined from config.
     */
    public JjwtBasedAuthTokenFactory(final Vertx vertx, final SignatureSupportingConfigProperties config) {

        super(vertx);
        Objects.requireNonNull(config);

        try {
            if (config.getSharedSecret() != null) {
                final var bytes = getBytes(config.getSharedSecret());
                final var secretKey = Keys.hmacShaKeyFor(bytes);
                this.signingKeyId = addSecretKey(secretKey);
                LOG.info("using shared secret [{} bytes] for signing/validating tokens", bytes.length);
            } else if (config.getKeyPath() != null && config.getCertPath() != null) {
                this.signingKeyId = addPrivateKey(config.getKeyPath(), config.getCertPath());
                LOG.info("using key pair [private: {}, cert: {}] for signing/verifying tokens",
                        config.getKeyPath(), config.getCertPath());
            } else {
                throw new IllegalArgumentException(
                        "configuration does not specify any key material for signing tokens");
            }
            tokenLifetime = Duration.ofSeconds(config.getTokenExpiration());
            LOG.info("using token lifetime of {} seconds", tokenLifetime.getSeconds());
            this.config = config;
            this.jwkSet = createJwkSet();
        } catch (final SecurityException e) {
            throw new IllegalArgumentException("failed to create factory for configured key material", e);
        }
    }

    @Override
    public Duration getTokenLifetime() {
        return tokenLifetime;
    }

    private JwkSet createJwkSet() {
        final var jwkSetBuilder = Jwks.set();
        getValidatingKeys().stream()
                .<Jwk<?>>mapMulti((entry, consumer) -> {
                    final var key = entry.getValue();
                    final var builder = Jwks.builder()
                            .id(entry.getKey())
                            .operations().add(Jwks.OP.VERIFY).and();
                    if (key instanceof SecretKey secretKey) {
                        consumer.accept(builder.key(secretKey).build());
                    } else if (key instanceof PublicKey publicKey) {
                        consumer.accept(builder.key(publicKey).build());
                    }
                })
                .forEach(jwkSetBuilder::add);
        final var jwkSet = jwkSetBuilder.build();
        LOG.info("successfully created JWK set containing {} keys", jwkSet.size());
        return jwkSet;
    }

    @Override
    public String createToken(final String authorizationId, final Authorities authorities) {

        Objects.requireNonNull(authorizationId);

        final var signingKey = getSigningKey(signingKeyId);
        final JwtBuilder builder = Jwts.builder()
                .header().keyId(signingKeyId).and()
                .issuer(config.getIssuer())
                .subject(authorizationId)
                .expiration(Date.from(Instant.now().plus(tokenLifetime)))
                .signWith(signingKey);
        Optional.ofNullable(authorities)
            .map(Authorities::asMap)
            .ifPresent(builder::claims);
        Optional.ofNullable(config.getAudience())
            .ifPresent(aud -> builder.audience().add(aud));
        return builder.compact();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JwkSet getValidatingJwkSet() {
        return jwkSet;
    }
}
