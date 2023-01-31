/**
 * Copyright (c) 2022-2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import javax.crypto.KeyGenerator;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.PrematureJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior of {@link ExternalJwtAuthTokenValidator}.
 */
class ExternalJwtAuthTokenValidatorTest {

    private static String validRsaX509CertString;
    private static String validEcX509CertString;
    private final String deviceId = "device-id";
    private final String authId = "auth-id";
    private ExternalJwtAuthTokenValidator authTokenValidator;
    private Map<String, Object> jwtHeader;
    private Instant instantNow;
    private Instant instantPlus24Hours;

    @BeforeAll
    static void init() {
        validRsaX509CertString = "MIIDezCCAmOgAwIBAgIUWAQ3roUv7ojy7Mz6Cp4gl1Fg2i0wDQYJKoZIhvcNAQEL" +
                "BQAwTDELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxEzARBgNVBAoM" +
                "ClRlc3RUZW5hbnQxEzARBgNVBAsMClRlc3REZXZpY2UwIBcNMjIxMjIyMTMyMDEw" +
                "WhgPMjA1MDA1MDgxMzIwMTBaMEwxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21l" +
                "LVN0YXRlMRMwEQYDVQQKDApUZXN0VGVuYW50MRMwEQYDVQQLDApUZXN0RGV2aWNl" +
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnfKbVA0wwEbKhvhYU/hs" +
                "UVh7r8ejiF7PHI5tNCzZ/LWKguFrRY+GOR3xElbNxjp3U+X8j7uzUoTiLjB6T+sg" +
                "D2XaEGVjAHIWg2IaqhHZn4Hmfd1EiJ9CDcII2zL3Sdr5pjxjL/RUWZSmdUbAo3Pj" +
                "5gr4B9OCBsf0u8/AQCBnGlse1cR9BKyYke/7DqDVyRmQGE4b/Nvom3bwG3h89qxX" +
                "RuipkwRK2OJg2McSufvS3HQwuZH2eTxG42op8Nhjk+2CH+wjYWjcivHvWXbjHFlz" +
                "9ZS2GmReuvz2T1X6H0OMK3Mj4OtF2tWWRddkNueRI+8wFwSA+BZAyyprcRFM6Gie" +
                "bQIDAQABo1MwUTAdBgNVHQ4EFgQUFP0CKVlsGb+NMn+w3ogkyDmXO/QwHwYDVR0j" +
                "BBgwFoAUFP0CKVlsGb+NMn+w3ogkyDmXO/QwDwYDVR0TAQH/BAUwAwEB/zANBgkq" +
                "hkiG9w0BAQsFAAOCAQEAJn1ffSpPQgZ8cXR11nrg58p82afDdaHDgvMDoGbYuPjU" +
                "pQIHm2gVpEshLW5codAvV1IDO8YLgeRJL3FBhKm2sbH5/vPtvjIYPKffYFKvMI6R" +
                "f85HRZZmPAkc0JPj6UnAJOcRzSQ8jvPfIvQ4HCXTJcreST/8y96qeZGG3mT1BckL" +
                "L9YHDUOSMfu68JZW/w8Ng2/WRHe3KFk1Mmu3vRGHpftbq5ntmgD8XgUmGp3wlwr0" +
                "QlA+pUO+vQKuR5xBStnsgad+g4XlKgW6XL8DbaHlOhQrYYmfEewAuILEd5h4cIir" +
                "U3JGN02ABUy2tcU4rdyoZ0VAnvhPOGCfMrYD6dnpbg==";
        validEcX509CertString = "MIIB7zCCAZWgAwIBAgIUZcrDSer0tQHRYK/jqQtZ3dSl47swCgYIKoZIzj0EAwIw" +
                "TDELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxEzARBgNVBAoMClRl" +
                "c3RUZW5hbnQxEzARBgNVBAsMClRlc3REZXZpY2UwIBcNMjIxMjIyMTM0MDEwWhgP" +
                "MjA1MDA1MDgxMzQwMTBaMEwxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0" +
                "YXRlMRMwEQYDVQQKDApUZXN0VGVuYW50MRMwEQYDVQQLDApUZXN0RGV2aWNlMFkw" +
                "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEVs/o5+uQbTjL3chynL4wXgUg2R9q9UU" +
                "8I5mEovUf86QZ7kOBIjJwqnzD1omageEHWwHdBO6B+dFabmdT9POxqNTMFEwHQYD" +
                "VR0OBBYEFJqoWPOmzWob7HYM8hKNpdNaxM50MB8GA1UdIwQYMBaAFJqoWPOmzWob" +
                "7HYM8hKNpdNaxM50MA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIg" +
                "Nb6jSmGvzPlpzRyboQKdBPiIR2hHgf1e3SBJngoubeQCIQCsZi+kFmtNCU6AELwz" +
                "UYEP5eqNVbGBJqnmKx5vx1KtEg==";
    }

    private static Stream<String> dataForParameterizedTest() {
        final List<String> data = new ArrayList<>();
         data.add("RS256,2048");
         data.add("RS256,4096");
         data.add("RS384,2048");
         data.add("RS512,2048");
         data.add("PS256,2048");
         data.add("PS384,2048");
         data.add("PS512,2048");
         data.add("ES256,256");
         data.add("ES384,384");
        return data.stream();
    }

    @BeforeEach
    void setUp() {
        authTokenValidator = new ExternalJwtAuthTokenValidator();
        jwtHeader = new HashMap<>();
        jwtHeader.put(JwsHeader.TYPE, "JWT");

        final long epochSecondNow = Instant.now().getEpochSecond();
        instantNow = Instant.ofEpochSecond(epochSecondNow);
        instantPlus24Hours = instantNow.plusSeconds(3600 * 24);
    }

    /**
     * Verifies that expand returns JWS Claims, when valid JWTs matching public keys are provided.
     */
    @ParameterizedTest
    @MethodSource(value = "dataForParameterizedTest")
    void testExpandValidJwtWithValidPublicKey(final String data) {
        final String[] parameters = data.split(",");
        final SignatureAlgorithm alg = SignatureAlgorithm.forName(parameters[0]);
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, Integer.parseInt(parameters[1]));
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        final Jws<Claims> jws = authTokenValidator.expand(jwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo(alg.getValue());

    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and multiple different public keys with the same
     * algorithm and within their validity period are provided.
     */
    @Test
    void testExpandValidJwtWithMultipleDifferentPublicKeysWithinTheirValidityPeriod() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair1 = generateKeyPair(alg, 256);
        final byte[] publicKey1 = keyPair1.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey1,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));
        final KeyPair keyPair2 = generateKeyPair(alg, 256);
        final byte[] publicKey2 = keyPair2.getPublic().getEncoded();
        final JsonObject secret = CredentialsObject.emptySecret(instantNow.minusSeconds(1500),
                instantNow.plusSeconds(5000));
        secret.put(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM, CredentialsConstants.EC_ALG);
        secret.put(CredentialsConstants.FIELD_SECRETS_KEY, publicKey2);
        authTokenValidator.getCredentialsObject().addSecret(secret);

        final String jwt1 = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair1.getPrivate());
        final Jws<Claims> jws1 = authTokenValidator.expand(jwt1);
        final String jwt2 = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair2.getPrivate());
        final Jws<Claims> jws2 = authTokenValidator.expand(jwt2);
        assertThat(jws1).isNotNull();
        assertThat(jws1.getHeader().getAlgorithm()).isEqualTo(alg.getValue());
        assertThat(jws2).isNotNull();
        assertThat(jws2.getHeader().getAlgorithm()).isEqualTo(alg.getValue());
    }

    /**
     * Verifies that expand throws a {@link RuntimeException}, when a valid but unsupported JWT is provided.
     */
    @Test
    void testExpandValidButUnsupportedJwt() {
        final SignatureAlgorithm alg = SignatureAlgorithm.HS256;
        final Key key = generateHmacKey();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), key.getEncoded(),
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, key);
        final RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authTokenValidator.expand(jwt));
        assertEquals("Provided algorithm is not supported.",
                exception.getMessage());
    }

    /**
     * Verifies that expand throws a {@link MalformedJwtException}, when an invalid JWT is provided.
     */

    @Test
    void testExpandInvalidJwtWithValidEcPublicKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate())
                        .replaceFirst("e", "a");
        assertThrows(MalformedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link UnsupportedJwtException}, when a JWT without an iat claim is provided.
     */
    @Test
    void testExpandIatClaimMissing() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, null, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(UnsupportedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link UnsupportedJwtException}, when a JWT without an exp claim is provided.
     */
    @Test
    void testExpandExpClaimMissing() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, null), alg, keyPair.getPrivate());
        assertThrows(UnsupportedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link PrematureJwtException}, when a JWT with an iat claim to far in the future
     * (greater current timestamp plus skew) is provided.
     */
    @Test
    void testExpandNotYetValidJwtWithValidEcPublicKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final int timeShiftSeconds = ExternalJwtAuthTokenValidator.ALLOWED_CLOCK_SKEW + 10;
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow.plusSeconds(timeShiftSeconds), instantPlus24Hours),
                alg, keyPair.getPrivate());
        assertThrows(PrematureJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link UnsupportedJwtException}, when a JWT with an iat claim too far in the past
     * (smaller current timestamp minus skew) is provided.
     */
    @Test
    void testExpandIatClaimTooFarInThePast() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);

        final int timeShiftSeconds = ExternalJwtAuthTokenValidator.ALLOWED_CLOCK_SKEW + 10;
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow.minusSeconds(timeShiftSeconds), instantPlus24Hours),
                alg, keyPair.getPrivate());
        assertThrows(UnsupportedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link UnsupportedJwtException}, when a JWT with an exp claim before or at the same
     * time as the iat claim is provided.
     */
    @Test
    void testExpandExpClaimNotAfterIatClaim() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);

        final int timeShiftSeconds = ExternalJwtAuthTokenValidator.ALLOWED_CLOCK_SKEW - 10;
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow.plusSeconds(timeShiftSeconds),
                        instantNow.plusSeconds(timeShiftSeconds)),
                alg, keyPair.getPrivate());
        assertThrows(UnsupportedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link UnsupportedJwtException}, when a JWT with an exp claim too far in the future
     * (greater iat claim plus 24 hours plus skew) is provided.
     */
    @Test
    void testExpandExpClaimTooFarInTheFuture() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);

        final int timeShiftSeconds = ExternalJwtAuthTokenValidator.ALLOWED_CLOCK_SKEW + 10;
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours.plusSeconds(timeShiftSeconds)),
                alg, keyPair.getPrivate());
        assertThrows(UnsupportedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link RuntimeException}, when an invalid public key is provided.
     */
    @Test
    void testExpandInvalidEcPublicKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        publicKey[0] = publicKey[1];
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(RuntimeException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link SignatureException}, when a valid JWT with a non-matching public key is
     * provided.
     */
    @Test
    void testExpandValidJwtWithNonMatchingEcPublicKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        keyPair = generateKeyPair(alg, 256);
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(SignatureException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link UnsupportedJwtException}, when a valid JWT with ES alg and valid EC
     * encrypted public key with a different size is provided.
     */
    @Test
    void testExpandValidJwtWithEcPublicKeyWithDifferentKeySize() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        KeyPair keyPair = generateKeyPair(alg, 384);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        keyPair = generateKeyPair(alg, 256);
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        final UnsupportedJwtException exception = assertThrows(UnsupportedJwtException.class,
                () -> authTokenValidator.expand(jwt));
        assertEquals("EllipticCurve key has a field size of 48 bytes (384 bits), but ES256 requires a field " +
                "size of 32 bytes (256 bits) per [RFC 7518, Section 3.4 (validation)]" +
                "(https://datatracker.ietf.org/doc/html/rfc7518#section-3.4).",
                exception.getCause().getMessage());
    }

    /**
     * Verifies that expand throws a {@link NullPointerException}, when no secret exists.
     */
    @Test
    void testExpandNoExistingSecret() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 2048);

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(NullPointerException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link NullPointerException}, when no matching secret exists.
     */
    @Test
    void testExpandNoExistingSecretWithSameAlgAsInJwtHeader() {
        SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        KeyPair keyPair = generateKeyPair(alg, 2048);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        keyPair = generateKeyPair(alg, 256);
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(NoSuchElementException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link SignatureException}, when the JWT header has no {@value JwsHeader#TYPE}
     * field.
     */
    @Test
    void testExpandTypFieldInJwtHeaderNull() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(new HashMap<>(),
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(MalformedJwtException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link SignatureException}, when the {@value JwsHeader#TYPE} field in the token
     * header is not "JWT".
     */
    @Test
    void testExpandTypFieldInJwtHeaderInvalid() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        jwtHeader.put(JwsHeader.TYPE, "invalid");
        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(MalformedJwtException.class, () -> authTokenValidator.expand(jwt));
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

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(tenantId, authId, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        final JsonObject claims = authTokenValidator.getJwtClaims(jwt);
        assertThat(claims.getString(Claims.ISSUER)).isEqualTo(tenantId);
        assertThat(claims.getString(Claims.SUBJECT)).isEqualTo(authId);
    }

    /**
     * Verifies that getJwtClaims throws a {@link MalformedJwtException}, when an invalid JWT is provided.
     */
    @Test
    void testGetJwtClaimsInvalidJwt() {
        final String jwt = "header.payload.signature";
        assertThrows(MalformedJwtException.class, () -> authTokenValidator.getJwtClaims(jwt));
    }

    /**
     * Verifies that convertPublicKeyStringToPublicKey returns a PublicKey object, when a valid raw public key with RSA
     * encryption is provided.
     */
    @Test
    void testConvertPublicKeyStringToPublicKeyValidRsaKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS384;
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        final String rpk = Base64.getEncoder().encodeToString(publicKeyBytes);

        final PublicKey publicKey = authTokenValidator.convertPublicKeyStringToPublicKey(rpk);
        assertThat(publicKey).isNotNull();
        assertThat(publicKey.getEncoded()).isEqualTo(publicKeyBytes);
    }

    /**
     * Verifies that convertPublicKeyStringToPublicKey returns a PublicKey object, when a valid raw public key with EC
     * encryption is provided.
     */
    @Test
    void testConvertPublicKeyStringToPublicKeyValidEcKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        final String rpk = Base64.getEncoder().encodeToString(publicKeyBytes);

        final PublicKey publicKey = authTokenValidator.convertPublicKeyStringToPublicKey(rpk);
        assertThat(publicKey).isNotNull();
        assertThat(publicKey.getEncoded()).isEqualTo(publicKeyBytes);
    }

    /**
     * Verifies that convertPublicKeyStringToPublicKey throws a {@link RuntimeException}, when an invalid raw public key
     * is provided.
     */
    @Test
    void testConvertPublicKeyStringToPublicKeyInvalidKey() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        final KeyPair keyPair = generateKeyPair(alg, 256);
        final byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        final String rpk = Base64.getEncoder().encodeToString(publicKeyBytes).replace("A", "I");

        assertThrows(RuntimeException.class, () -> authTokenValidator.convertPublicKeyStringToPublicKey(rpk));
    }

    /**
     * Verifies that convertX509CertStringToPublicKey returns a PublicKey object, when a valid x509 Certificate with RSA
     * encrypted public key is provided.
     */
    @Test
    void testConvertX509CertStringToPublicKeyValidRsaX509Certificate() throws CertificateException {
        final PublicKey publicKey = authTokenValidator.convertX509CertStringToPublicKey(validRsaX509CertString);
        assertThat(publicKey).isNotNull();
        assertThat(publicKey.getAlgorithm()).isEqualTo(CredentialsConstants.RSA_ALG);
    }

    /**
     * Verifies that convertX509CertStringToPublicKey returns a PublicKey object, when a valid x509 Certificate with EC
     * encrypted public key is provided.
     */
    @Test
    void testConvertX509CertStringToPublicKeyValidEcX509Certificate() throws CertificateException {
        final PublicKey publicKey = authTokenValidator.convertX509CertStringToPublicKey(validEcX509CertString);
        assertThat(publicKey).isNotNull();
        assertThat(publicKey.getAlgorithm()).isEqualTo(CredentialsConstants.EC_ALG);
    }

    /**
     * Verifies that convertX509CertStringToPublicKey throws a {@link CertificateException}, when an invalid x509
     * Certificate is provided.
     */
    @Test
    void testConvertX509CertStringToPublicKeyInvalidX509Certificate() {
        assertThrows(CertificateException.class,
                () -> authTokenValidator.convertX509CertStringToPublicKey(validEcX509CertString.replace("M", "I")));

    }

    private String generateJwt(final Map<String, Object> header, final Map<String, Object> claims,
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

    private Map<String, Object> generateJwtClaims(final String iss, final String sub, final Instant iat,
            final Instant exp) {
        final Map<String, Object> jwtClaims = new HashMap<>();
        jwtClaims.put(Claims.ISSUER, iss);
        jwtClaims.put(Claims.SUBJECT, sub);
        if (iat != null) {
            jwtClaims.put(Claims.ISSUED_AT, iat.toString());
        }
        if (exp != null) {
            jwtClaims.put(Claims.EXPIRATION, exp.toString());
        }
        return jwtClaims;
    }
}
