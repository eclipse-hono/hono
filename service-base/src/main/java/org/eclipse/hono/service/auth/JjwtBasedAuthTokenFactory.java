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

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.util.CredentialsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A JJWT based factory for creating signed JSON Web Tokens containing user claims.
 *
 */
@RegisterForReflection(targets = {
                        io.jsonwebtoken.impl.DefaultJwtBuilder.class,
                        io.jsonwebtoken.jackson.io.JacksonSerializer.class,
                        io.jsonwebtoken.impl.compression.GzipCompressionCodec.class
})
public final class JjwtBasedAuthTokenFactory extends JwtSupport implements AuthTokenFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JjwtBasedAuthTokenFactory.class);
    private static final String PROPERTY_JWK_ALG = "alg";
    private static final String PROPERTY_JWK_CRV = "crv";
    private static final String PROPERTY_JWK_E = "e";
    private static final String PROPERTY_JWK_KEY_OPS = "key_ops";
    private static final String PROPERTY_JWK_K = "k";
    private static final String PROPERTY_JWK_KID = "kid";
    private static final String PROPERTY_JWK_KTY = "kty";
    private static final String PROPERTY_JWK_N = "n";
    private static final String PROPERTY_JWK_USE = "use";
    private static final String PROPERTY_JWK_X = "x";
    private static final String PROPERTY_JWK_Y = "y";

    private static final Map<String, String> CURVE_OID_TO_NIST_NAME = Map.of(
            "1.2.840.10045.3.1.7", "P-256",
            "1.3.132.0.34", "P-384",
            "1.3.132.0.35", "P-521");

    private final JsonObject jwkSet = new JsonObject();
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
            createJwk();
        } catch (final SecurityException e) {
            throw new IllegalArgumentException("failed to create factory for configured key material", e);
        }
    }

    @Override
    public Duration getTokenLifetime() {
        return tokenLifetime;
    }

    private void createJwk() {
        final JsonArray keyArray = getValidatingKeys().stream()
                .map(entry -> {
                    final var jwk = new JsonObject();
                    final var keyId = entry.getKey();
                    final var keySpec = entry.getValue();
                    jwk.put(PROPERTY_JWK_USE, "sig");
                    jwk.put(PROPERTY_JWK_KEY_OPS, "verify");
                    if (keySpec.key instanceof SecretKey secretKey) {
                        jwk.put(PROPERTY_JWK_KTY, "oct");
                        jwk.put(PROPERTY_JWK_K, keySpec.key.getEncoded());
                    } else {
                        addPublicKey(jwk, keySpec);
                    }
                    jwk.put(PROPERTY_JWK_KID, keyId);
                    jwk.put(PROPERTY_JWK_ALG, keySpec.algorithm.getValue());
                    return jwk;
                })
                .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
        jwkSet.put("keys", keyArray);
        if (LOG.isInfoEnabled()) {
            LOG.info("successfully created JWK set:{}{}", System.lineSeparator(), jwkSet.encodePrettily());
        }
    }

    private void addPublicKey(final JsonObject jwk, final KeySpec keySpec) {

        if (keySpec.key instanceof RSAPublicKey rsaPublicKey) {
            addRsaPublicKey(jwk, rsaPublicKey);
        } else if (keySpec.key instanceof ECPublicKey ecPublicKey) {
            addEcPublicKey(jwk, ecPublicKey);
        } else {
            throw new IllegalArgumentException(
                    "unsupported key type [%s], must be RSA or EC".formatted(keySpec.key.getAlgorithm()));
        }
        jwk.put(PROPERTY_JWK_KTY, keySpec.key.getAlgorithm());
    }

    private void addRsaPublicKey(final JsonObject jwk, final RSAPublicKey rsaPublicKey) {
        // according to https://datatracker.ietf.org/doc/html/rfc7518#section-6.3.1
        // the modulus and exponent need to be base64url encoded without padding
        final var encoder = Base64.getUrlEncoder().withoutPadding();
        jwk.put(PROPERTY_JWK_N, encoder.encodeToString(rsaPublicKey.getModulus().toByteArray()));
        jwk.put(PROPERTY_JWK_E, encoder.encodeToString(rsaPublicKey.getPublicExponent().toByteArray()));
    }

    private String getNistCurveName(final ECPublicKey ecPublicKey) throws GeneralSecurityException {
        final AlgorithmParameters parameters = AlgorithmParameters.getInstance(CredentialsConstants.EC_ALG);
        parameters.init(ecPublicKey.getParams());
        final var spec = parameters.getParameterSpec(ECGenParameterSpec.class);
        final var oid = spec.getName();
        return Optional.ofNullable(CURVE_OID_TO_NIST_NAME.get(oid))
                .orElseThrow(() -> new IllegalArgumentException("unsupported curve [%s]".formatted(oid)));
    }

    private void addEcPublicKey(final JsonObject jwk, final ECPublicKey ecPublicKey) {
        try {
            jwk.put(PROPERTY_JWK_CRV, getNistCurveName(ecPublicKey));
            // according to https://datatracker.ietf.org/doc/html/rfc7518#section-6.2.1.2
            // the X and Y coordinates need to be base64url encoded without padding
            final var encoder = Base64.getUrlEncoder().withoutPadding();
            jwk.put(PROPERTY_JWK_X, encoder.encodeToString(ecPublicKey.getW().getAffineX().toByteArray()));
            jwk.put(PROPERTY_JWK_Y, encoder.encodeToString(ecPublicKey.getW().getAffineY().toByteArray()));
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("cannot serialize EC based public key", e);
        }
    }

    @Override
    public String createToken(final String authorizationId, final Authorities authorities) {

        Objects.requireNonNull(authorizationId);

        final var signingKeySpec = getSigningKey(signingKeyId);
        final JwtBuilder builder = Jwts.builder()
                .signWith(signingKeySpec.key, signingKeySpec.algorithm)
                .setHeaderParam(PROPERTY_JWK_KID, signingKeyId)
                .setIssuer(config.getIssuer())
                .setSubject(authorizationId)
                .setExpiration(Date.from(Instant.now().plus(tokenLifetime)));
        Optional.ofNullable(authorities)
            .map(Authorities::asMap)
            .ifPresent(authMap -> authMap.forEach(builder::claim));
        Optional.ofNullable(config.getAudience())
            .ifPresent(builder::setAudience);
        return builder.compact();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonObject getValidatingJwkSet() {
        return jwkSet;
    }
}
