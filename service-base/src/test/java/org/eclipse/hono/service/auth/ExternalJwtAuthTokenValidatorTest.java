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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
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

    private static String rsaPrivateKeyString;
    private static String validRsaX509CertString;
    private static String ecPrivateKeyString;
    private static String validEcX509CertString;
    private final String deviceId = "device-id";
    private final String authId = "auth-id";
    private ExternalJwtAuthTokenValidator authTokenValidator;
    private Map<String, Object> jwtHeader;
    private Instant instantNow;
    private Instant instantPlus24Hours;

    @BeforeAll
    static void init() {
        rsaPrivateKeyString = "-----BEGIN PRIVATE KEY-----" +
                "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCd8ptUDTDARsqG" +
                "+FhT+GxRWHuvx6OIXs8cjm00LNn8tYqC4WtFj4Y5HfESVs3GOndT5fyPu7NShOIu" +
                "MHpP6yAPZdoQZWMAchaDYhqqEdmfgeZ93USIn0INwgjbMvdJ2vmmPGMv9FRZlKZ1" +
                "RsCjc+PmCvgH04IGx/S7z8BAIGcaWx7VxH0ErJiR7/sOoNXJGZAYThv82+ibdvAb" +
                "eHz2rFdG6KmTBErY4mDYxxK5+9LcdDC5kfZ5PEbjainw2GOT7YIf7CNhaNyK8e9Z" +
                "duMcWXP1lLYaZF66/PZPVfofQ4wrcyPg60Xa1ZZF12Q255Ej7zAXBID4FkDLKmtx" +
                "EUzoaJ5tAgMBAAECggEAAjeo7rzLPaIXpjmUeii+UtbkYsi6pvDS8tOcxd6IL33Y" +
                "HXghaFXz2heSKX2I6eGYrSDJF9sz0O4d8Vp94IfH36icCueZe24MUd1yXfUC6TQm" +
                "KXV72uZPw5ZlkG7QqzuOILEdV6c4sDho9iGIgz5eoFjwpX0yCJ+fmHTVRzEx1e6C" +
                "T6x5qNx16xLVBZACsWCHZ6AsVMpiqpO/G/oPnWky37gV/W9eMZkn34+ubN5QZ/vG" +
                "AwkNBR3fCthUVsgTfw5wdSufChnKElI90eN+ypR5ONzLYVDjoDO4pJTiwLCUbmOM" +
                "6JAC+los1j7vChNvZJ3iQJ4FngBxOwJAckZJMCKDkQKBgQC1rHhPuTBAymtCbEFX" +
                "yZnSGGsDYEM22xQyXAgw8bqOjTcc/slKwHjPy39EdzIXMA5fJQxYU5QAd+OgXjGu" +
                "RklPKNFv9kXogddEVtmzCQxohrMsk5wdSa/LbVhazKAGnIyLHXWgxYD/fGIaQCNg" +
                "q/Zlb+h9DhC6EujF+08po2KctQKBgQDekTMpce3EUqrz3nGgO53QHyConFFMV/2g" +
                "/L432tK9o3co+8FQeeXJ2geOWfRToOZyLWq2aBt2LV/uIB6mwho6bWTF+pi9WcQd" +
                "v2k56X3KpiWJ+QYe1gbgMM3Psg/Sj0iSzz9RO2gXtpTy31Jl6nhC+JjR4lVlUyQq" +
                "WgdewWFF2QKBgQCFGTxzvAs8DJCUc1dUB6EoKTeNm6Lit5KOapqdsRuqgI8WMRws" +
                "JeLc6gvtjx4lmtGMp0nqFCFkTnF39kqTkW74DcGTM2x4MVgS+0Y3QrPSiI0QZXyE" +
                "gI3Ije2jaDL9ZQgai5S4GrqtcuU0sjS5CINWQaykof9jM6NSGRIgQVFn2QKBgCCG" +
                "JWzUCkPbNMIoaoBY1en48oPRPAwk+5pP9NgisRMnVR13FLvW5F6H7vy9ZnfmFmbu" +
                "/h4jvoeZf+BDb1c9HCoXnFdWFIXvHTqfoxfkaA56ExhDfMJ60kxmtVy5j5hceeWC" +
                "RaVwQfjdJI0NV3QvPF3FCEf7hDEnYiySNWuCZN2ZAoGBAK3jTQuunm/yQqKFcqDw" +
                "MwUWIugnvi6DYwweLRZLEdK29is58rbpe1c5dc9N2oVy3W1x59YQ9t3OdRZ4sTnT" +
                "HOHL/Bvb7Cc7nn/a751eul6xNAio4SJmYzPrK50uwXO72LpyDiw/+MY0aEL8BQhy" +
                "sdQ915R/NbECtu0DTZVHJ0my" +
                "-----END PRIVATE KEY-----";
        validRsaX509CertString = "-----BEGIN CERTIFICATE-----" +
                "MIIDezCCAmOgAwIBAgIUWAQ3roUv7ojy7Mz6Cp4gl1Fg2i0wDQYJKoZIhvcNAQEL" +
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
                "U3JGN02ABUy2tcU4rdyoZ0VAnvhPOGCfMrYD6dnpbg==" +
                "-----END CERTIFICATE-----";
        ecPrivateKeyString = "-----BEGIN PRIVATE KEY-----" +
                "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgevZzL1gdAFr88hb2" +
                "OF/2NxApJCzGCEDdfSp6VQO30hyhRANCAAQRWz+jn65BtOMvdyHKcvjBeBSDZH2r" +
                "1RTwjmYSi9R/zpBnuQ4EiMnCqfMPWiZqB4QdbAd0E7oH50VpuZ1P087G" +
                "-----END PRIVATE KEY-----";
        validEcX509CertString = "-----BEGIN CERTIFICATE-----" +
                "MIIB7zCCAZWgAwIBAgIUZcrDSer0tQHRYK/jqQtZ3dSl47swCgYIKoZIzj0EAwIw" +
                "TDELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxEzARBgNVBAoMClRl" +
                "c3RUZW5hbnQxEzARBgNVBAsMClRlc3REZXZpY2UwIBcNMjIxMjIyMTM0MDEwWhgP" +
                "MjA1MDA1MDgxMzQwMTBaMEwxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0" +
                "YXRlMRMwEQYDVQQKDApUZXN0VGVuYW50MRMwEQYDVQQLDApUZXN0RGV2aWNlMFkw" +
                "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEVs/o5+uQbTjL3chynL4wXgUg2R9q9UU" +
                "8I5mEovUf86QZ7kOBIjJwqnzD1omageEHWwHdBO6B+dFabmdT9POxqNTMFEwHQYD" +
                "VR0OBBYEFJqoWPOmzWob7HYM8hKNpdNaxM50MB8GA1UdIwQYMBaAFJqoWPOmzWob" +
                "7HYM8hKNpdNaxM50MA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIg" +
                "Nb6jSmGvzPlpzRyboQKdBPiIR2hHgf1e3SBJngoubeQCIQCsZi+kFmtNCU6AELwz" +
                "UYEP5eqNVbGBJqnmKx5vx1KtEg==" +
                "-----END CERTIFICATE-----";
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
     * Verifies that expand returns JWS Claims, when a valid JWT and valid x509 Certificate with RSA encrypted public
     * key is provided.
     */
    @Test
    void testExpandValidJwtWithValidRsaX509Certificate() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final Key privateKey = getPrivateKeyFromString(rsaPrivateKeyString, alg);

        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.RSA_ALG,
                        validRsaX509CertString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, privateKey);
        final Jws<Claims> jws = authTokenValidator.expand(jwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo(alg.getValue());
    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and valid X509 Certificate with EC encrypted public key
     * is provided.
     */
    @Test
    void testExpandValidJwtWithValidEcX509Certificate() {
        final SignatureAlgorithm alg = SignatureAlgorithm.ES256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final Key privateKey = getPrivateKeyFromString(ecPrivateKeyString, alg);

        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, validEcX509CertString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, privateKey);
        System.out.println(jwt);
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
        final String publicKey1 = generatePublicKeyString(keyPair1.getPublic());
        System.out.println(publicKey1);
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, CredentialsConstants.EC_ALG, publicKey1,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));
        final KeyPair keyPair2 = generateKeyPair(alg, 256);
        final String publicKey2 = generatePublicKeyString(keyPair2.getPublic());
        System.out.println(publicKey2);
        final JsonObject secret = CredentialsObject.emptySecret(instantNow.minusSeconds(1500),
                instantNow.plusSeconds(5000));
        secret.put(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM, CredentialsConstants.EC_ALG);
        secret.put(CredentialsConstants.FIELD_SECRETS_KEY, publicKey2);
        authTokenValidator.getCredentialsObject().addSecret(secret);

        final String jwt1 = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair1.getPrivate());
        System.out.println(jwt1);
        final Jws<Claims> jws1 = authTokenValidator.expand(jwt1);
        final String jwt2 = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair2.getPrivate());
        System.out.println(jwt2);
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
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), generatePublicKeyString(key),
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, key);
        final RuntimeException exception = assertThrows(RuntimeException.class,
                () -> authTokenValidator.expand(jwt));
        assertEquals("io.jsonwebtoken.UnsupportedJwtException: Alg provided in the JWT header is not supported.",
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic()).replaceFirst("M", "a");
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
     * Verifies that expand throws a {@link IllegalArgumentException}, when the key in the secret has an invalid syntax.
     */
    @Test
    void testExpandKeyInSecretHasInvalidSyntax() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        jwtHeader.put(JwsHeader.ALGORITHM, alg.getValue());
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final String publicKey = generatePublicKeyString(keyPair.getPublic())
                .replace(CredentialsConstants.BEGIN_KEY, "")
                .replace(CredentialsConstants.END_KEY, "");
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromRawPublicKey(deviceId, authId, alg.getFamilyName(), publicKey,
                        instantNow.minusSeconds(3600), instantNow.plusSeconds(3600)));

        final String jwt = generateJwt(jwtHeader,
                generateJwtClaims(null, null, instantNow, instantPlus24Hours), alg, keyPair.getPrivate());
        assertThrows(IllegalArgumentException.class, () -> authTokenValidator.expand(jwt));
    }

    /**
     * Verifies that expand throws a {@link SignatureException}, when the JWT header has no {@value JwsHeader#TYPE}
     * field.
     */
    @Test
    void testExpandTypFieldInJwtHeaderNull() {
        final SignatureAlgorithm alg = SignatureAlgorithm.RS256;
        final KeyPair keyPair = generateKeyPair(alg, 2048);
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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
        final String publicKey = generatePublicKeyString(keyPair.getPublic());
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

    private String generatePublicKeyString(final Key publicKey) {
        final String key = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return CredentialsConstants.BEGIN_KEY + key + CredentialsConstants.END_KEY;
    }

    private Key getPrivateKeyFromString(final String key, final SignatureAlgorithm alg) {
        try {
            String privateKeyPEM = key;
            privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "");
            privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
            final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyPEM));
            final KeyFactory keyFactory;
            if (alg.isRsa()) {
                keyFactory = KeyFactory.getInstance(CredentialsConstants.RSA_ALG);
            } else if (alg.isEllipticCurve()) {
                keyFactory = KeyFactory.getInstance(CredentialsConstants.EC_ALG);
            } else {
                throw new UnsupportedJwtException("Alg provided in the JWT header is not supported.");
            }
            return keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
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
