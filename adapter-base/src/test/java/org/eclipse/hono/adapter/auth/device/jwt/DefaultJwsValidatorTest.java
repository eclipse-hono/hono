/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;

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
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
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
    private Map<String, Object> jwtHeader;
    private Instant instantNow;
    private Instant instantPlus24Hours;

    @BeforeEach
    void setUp() {
        authTokenValidator = new DefaultJwsValidator();
        jwtHeader = new HashMap<>();
        jwtHeader.put(JwsHeader.TYPE, "JWT");

        instantNow = Instant.now();
        instantPlus24Hours = instantNow.plus(Duration.ofHours(24));
    }

    /**
     * Verifies that expand returns JWS Claims, when valid JWTs matching public keys are provided.
     */
    @ParameterizedTest
    @CsvSource(value = {
        "RS256,2048",
        "RS256,4096",
        "RS384,2048",
        "RS512,2048",
        "PS256,2048",
        "PS384,2048",
        "PS512,2048",
        "ES256,256",
        "ES384,384"
    })
    void testExpandValidJwtWithValidPublicKey(final String algorithm, final int keySize, final VertxTestContext ctx) {
        final SignatureAlgorithm alg = SignatureAlgorithm.forName(algorithm);
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, keySize);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));

        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        authTokenValidator.expand(jwt, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .onComplete(ctx.succeeding(jws -> {
                ctx.verify(() -> {
                    assertThat(jws.getHeader().getAlgorithm()).isEqualTo(alg.getValue());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and multiple different public keys with the same
     * algorithm and within their validity period are provided.
     */
    @Test
    void testExpandValidJwtWithMultipleDifferentPublicKeysWithinTheirValidityPeriod(final VertxTestContext ctx) {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair1 = generateKeyPair(alg, 256);
        final KeyPair keyPair2 = generateKeyPair(alg, 256);

        final JsonObject secondSecret = CredentialsObject.emptySecret(
                instantNow.minusSeconds(1500),
                instantNow.plusSeconds(5000))
            .put(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM, CredentialsConstants.EC_ALG)
            .put(CredentialsConstants.FIELD_SECRETS_KEY, keyPair2.getPublic().getEncoded());

        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                keyPair1.getPublic().getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));
        creds.addSecret(secondSecret);

        final String jwt1 = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours),
                alg,
                keyPair1.getPrivate());
        final String jwt2 = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours),
                alg,
                keyPair2.getPrivate());

        authTokenValidator.expand(jwt1, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW)
            .compose(jws1 -> {
                ctx.verify(() -> {
                    assertThat(jws1.getBody().getExpiration().toInstant()).isAtMost(instantPlus24Hours);
                });
                return authTokenValidator.expand(jwt2, creds.getCandidateSecrets(), ALLOWED_CLOCK_SKEW);
            })
            .onComplete(ctx.succeeding(jws2 -> {
                ctx.verify(() -> {
                    assertThat(jws2.getBody().getExpiration().toInstant()).isAtMost(instantPlus24Hours);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that expand fails, when a valid but unsupported JWT is provided.
     */
    @Test
    void testExpandFailsForJwsUsingHmacKey(final VertxTestContext ctx) {
        final SignatureAlgorithm alg = SignatureAlgorithm.HS256;
        final Key key = generateHmacKey();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                alg.getFamilyName(),
                key.getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, key);
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
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));

        final String jwt = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate())
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
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);

        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(), instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));
        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(null, null, null, instantPlus24Hours), alg, keyPair.getPrivate());
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
    void testExpandExpClaimMissing(final VertxTestContext ctx) {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final var creds = CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(), instantNow.minusSeconds(3600), instantNow.plusSeconds(3600));

        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(null, null, instantNow, null), alg, keyPair.getPrivate());
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

        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final var tooFarInTheFuture = instantNow.plus(ALLOWED_CLOCK_SKEW).plusSeconds(5);
        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(null, null, tooFarInTheFuture, instantPlus24Hours),
                alg, keyPair.getPrivate());
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
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantNow),
                alg,
                keyPair.getPrivate());
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
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                keyPair.getPublic().getEncoded(),
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours.plus(ALLOWED_CLOCK_SKEW).plusSeconds(10)),
                alg,
                keyPair.getPrivate());
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
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        publicKey[0] = publicKey[1];
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG, publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours),
                alg,
                keyPair.getPrivate());

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
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.EC_ALG,
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        keyPair = generateKeyPair(alg, 256);
        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
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
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 2048);

        final String jwt = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours),
                alg,
                keyPair.getPrivate());

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
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                alg.getFamilyName(),
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        final String jwt = generateJws(
                Map.of(),
                generateJwtClaims(null, null, instantNow, instantPlus24Hours),
                alg,
                keyPair.getPrivate());

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
    void testExpandTypFieldInJwtHeaderInvalid(final VertxTestContext ctx) {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final var creds = CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                alg.getFamilyName(),
                publicKey,
                instantNow.minusSeconds(3600),
                instantNow.plusSeconds(3600));

        jwtHeader.put(JwsHeader.TYPE, "invalid");
        final String jwt = generateJws(
                jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours),
                alg,
                keyPair.getPrivate());

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
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 2048);

        final String jwt = generateJws(jwtHeader,
                generateJwtClaims(tenantId, authId, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
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

    private String generateJws(final Map<String, Object> header, final Map<String, Object> claims,
            final SignatureAlgorithm alg, final Key key) {
        final JwtBuilder jwtBuilder = Jwts.builder().setHeaderParams(header).setClaims(claims).signWith(key, alg);
        return jwtBuilder.compact();
    }

    private KeyPair generateKeyPair(final SignatureAlgorithm alg, final int keySize) {
        String algType = alg.getFamilyName();
        if (alg.isEllipticCurve()) {
            algType = CredentialsConstants.EC_ALG;
        }
        try {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algType);
            keyPairGenerator.initialize(keySize);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Key generateHmacKey() {
        try {
            final KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> generateJwtClaims(
            final String iss,
            final String sub,
            final Instant iat,
            final Instant exp) {

        final Map<String, Object> jwtClaims = new HashMap<>();
        jwtClaims.put(Claims.ISSUER, iss);
        jwtClaims.put(Claims.SUBJECT, sub);
        if (iat != null) {
            jwtClaims.put(Claims.ISSUED_AT, Date.from(iat));
        }
        if (exp != null) {
            jwtClaims.put(Claims.EXPIRATION, Date.from(exp));
        }
        return jwtClaims;
    }
}
