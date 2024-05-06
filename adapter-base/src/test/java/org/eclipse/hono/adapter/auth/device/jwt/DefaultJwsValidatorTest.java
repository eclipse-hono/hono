/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.auth.device.jwt;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureAlgorithm;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link DefaultJwsValidator}.
 */
@ExtendWith(VertxExtension.class)
class DefaultJwsValidatorTest {

    private final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(10);

    private final String deviceId = "device-id";
    private final String authId = "auth-id";
    private DefaultJwsValidator authTokenValidator;
    private Instant instantNow;
    private Instant instantPlus24Hours;

    @BeforeEach
    void setUp() {
        authTokenValidator = new DefaultJwsValidator();

        instantNow = Instant.now();
        instantPlus24Hours = instantNow.plus(Duration.ofHours(24));
    }

    JwtBuilder jwtBuilder() {
        return Jwts.builder().header().type("JWT").and();
    }

    /**
     * Verifies that expand returns JWS Claims, when valid JWTs matching public keys are provided.
     */
    @ParameterizedTest
    @CsvSource(value = {
        "RS256,RSA,2048",
        "RS256,RSA,4096",
        "RS384,RSA,2048",
        "RS512,RSA,2048",
        "PS256,RSA,2048",
        "PS384,RSA,2048",
        "PS512,RSA,2048",
        "ES256,EC,256",
        "ES384,EC,384"
    })
    void testExpandValidJwtWithValidPublicKey(
            final String algorithm,
            final String algType,
            final int keySize,
            final VertxTestContext ctx) throws GeneralSecurityException {
        final var alg = Jwts.SIG.get().forKey(algorithm);
        if (alg instanceof SignatureAlgorithm sigAlg) {
            final var keyPair = generateKeyPair(algType, keySize);
            final byte[] publicKey = keyPair.getPublic().getEncoded();
            final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, keyPair.getPublic().getAlgorithm(), publicKey,
                            instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));
            final String jwt = jwtBuilder()
                    .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                    .signWith(keyPair.getPrivate(), sigAlg)
                    .compact();
            authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
                .onComplete(ctx.succeeding(jws -> {
                    ctx.verify(() -> {
                        assertThat(jws.getHeader().getAlgorithm()).isEqualTo(alg.getId());
                    });
                    ctx.completeNow();
                }));
        } else {
            org.junit.Assert.fail("not a signature algorithm");
        }

    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and multiple different public keys with the same
     * algorithm and within their validity period are provided.
     */
    @Test
    void testExpandValidJwtWithMultipleDifferentPublicKeysWithinTheirValidityPeriod(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair1 = alg.keyPair().build();
        final KeyPair keyPair2 = alg.keyPair().build();

        final JsonObject secondSecret = CredentialsObject.emptySecret(
                instantNow.minusSeconds(1500),
                instantNow.plusSeconds(5000))
            .put(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM, keyPair2.getPublic().getAlgorithm())
            .put(CredentialsConstants.FIELD_SECRETS_KEY, keyPair2.getPublic().getEncoded());

        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                keyPair1.getPublic().getAlgorithm(),
                keyPair1.getPublic().getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));
        creds.addSecret(secondSecret);

        final String jwt1 = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair1.getPrivate())
                .compact();
        final String jwt2 = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair2.getPrivate())
                .compact();

        authTokenValidator.expand(jwt1, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .compose(jws1 -> {
                ctx.verify(() -> {
                    assertThat(jws1.getPayload().getExpiration().toInstant()).isAtMost(instantPlus24Hours);
                });
                return authTokenValidator.expand(jwt2, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW);
            })
            .onComplete(ctx.succeeding(jws2 -> {
                ctx.verify(() -> {
                    assertThat(jws2.getPayload().getExpiration().toInstant()).isAtMost(instantPlus24Hours);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails, when a valid but unsupported JWT is provided.
     */
    @Test
    void testExpandFailsForJwsUsingHmacKey(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.HS256;
        final Key key = alg.key().build();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                key.getAlgorithm(),
                key.getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(key)
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                assertThat(t).isInstanceOf(ClientErrorException.class);
                assertThat(ServiceInvocationException.extractStatusCode(t))
                    .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails when an invalid JWS is provided.
     */
    @Test
    void testExpandFailsForMalformedJws(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact()
                .replaceFirst("e", "a");

        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for a token that does not contain an <em>iat</em> claim.
     */
    @Test
    void testExpandFailsForTokenWithoutIat(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();

        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(), instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));
        final String jwt = jwtBuilder()
                .claims().expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for a token that does not contain an <em>exp</em> claim.
     */
    @Test
    void testExpandFailsForTokenWithoutExpiration(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();
        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(), instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).and()
                .signWith(keyPair.getPrivate())
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for a token that has been issued too far in the past.
     */
    @Test
    void testExpandNotYetValidJwtWithValidEcPublicKey(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final var tooFarInTheFuture = instantNow.plus(ALLOWED_CLOCK_SKEW).plusSeconds(5);
        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(tooFarInTheFuture)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for a token that expires at the same instant as it has been issued.
     */
    @Test
    void testExpandFailsForTokenWithExpNotAfterIat(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantNow)).and()
                .signWith(keyPair.getPrivate())
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for tokens that have a validity period exceeding 24 hours plus skew.
     */
    @Test
    void testExpandFailsForTokenExceedingMaxValidityPeriod(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours.plus(ALLOWED_CLOCK_SKEW).plusSeconds(10))).and()
                .signWith(keyPair.getPrivate())
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails if the candidate keys on record cannot be deserialized.
     */
    @Test
    void testExpandFailsIfCandidateKeyCannotBeDeserialized(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        final KeyPair keyPair = alg.keyPair().build();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        publicKey[0] = publicKey[1];
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG, publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();

        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails if none of the RPKs on record match the private key used to create the JWS.
     */
    @Test
    void testExpandFailsForNonMatchingPublicKey(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.ES256;
        KeyPair keyPair = alg.keyPair().build();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        keyPair = alg.keyPair().build();
        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand throws a {@link NullPointerException}, when no secret exists.
     */
    @Test
    void testExpandFailsIfNoCandidateKeyExist(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.RS256;
        final KeyPair keyPair = alg.keyPair().build();

        final String jwt = jwtBuilder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();

        authTokenValidator.expand(jwt, List.of(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for a token that has no <em>typ</em> header.
     */
    @Test
    void testExpandFailsForTokenWithoutTypeHeader(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.RS256;
        final KeyPair keyPair = alg.keyPair().build();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                keyPair.getPrivate().getAlgorithm(),
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = Jwts.builder()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();

        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails for a token that has a <em>typ</em> header with a value other than {@code JWT}.
     */
    @Test
    void testExpandFailsForTokenWithInvalidType(final VertxTestContext ctx) {
        final var alg = Jwts.SIG.RS256;
        final KeyPair keyPair = alg.keyPair().build();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                keyPair.getPrivate().getAlgorithm(),
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = Jwts.builder()
                .header().type("invalid").and()
                .claims().issuedAt(Date.from(instantNow)).expiration(Date.from(instantPlus24Hours)).and()
                .signWith(keyPair.getPrivate())
                .compact();

        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(ServiceInvocationException.extractStatusCode(t))
                        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that getJwtClaims returns JsonObject, when a valid JWT is provided.
     */
    @Test
    void testGetJwtClaimsValidJwt() {
        final String tenantId = "tenant-id";
        final var alg = Jwts.SIG.RS256;
        final KeyPair keyPair = alg.keyPair().build();

        final String jwt = jwtBuilder()
                .claims()
                    .issuer(tenantId)
                    .subject(authId)
                    .issuedAt(Date.from(instantNow))
                    .expiration(Date.from(instantPlus24Hours))
                    .and()
                .signWith(keyPair.getPrivate())
                .compact();
        final JsonObject claims = DefaultJwsValidator.getJwtClaims(jwt);
        assertThat(claims.getString(Claims.ISSUER)).isEqualTo(tenantId);
        assertThat(claims.getString(Claims.SUBJECT)).isEqualTo(authId);
    }

    /**
     * Verifies that getJwtClaims throws a {@link MalformedJwtException}, when an invalid JWT is provided.
     */
    @Test
    void testGetJwtClaimsInvalidJwt() {
        final String jwt = "header.payload.signature";
        assertThrows(MalformedJwtException.class, () -> DefaultJwsValidator.getJwtClaims(jwt));
    }

    private KeyPair generateKeyPair(final String algType, final int keySize) throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algType);
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }
}
