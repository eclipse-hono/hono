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

package org.eclipse.hono.service.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

/**
 * Verifies behavior of {@link ExternalJwtAuthTokenValidator}.
 */
class ExternalJwtAuthTokenValidatorTest {

    private static String validRsPublicKeyString;
    private static String validEsPublicKeyString;
    private static String invalidEsPublicKeyString;
    private static String validRsX509CertString;
    private static String validEsX509CertString;
    private static String validRsJwt;
    private static String validEsJwt;
    private static String invalidEsJwt;
    private static String invalidAlgFieldRsJwt;
    private final String deviceId = "deviceId";
    private final String authId = "authId";
    private ExternalJwtAuthTokenValidator authTokenValidator;

    @BeforeAll
    static void init() {
        validRsPublicKeyString = "-----BEGIN PUBLIC KEY-----" +
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo" +
                "4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u" +
                "+qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh" +
                "kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ" +
                "0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg" +
                "cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc" +
                "mwIDAQAB" +
                "-----END PUBLIC KEY-----";
        validEsPublicKeyString = "-----BEGIN PUBLIC KEY-----" +
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEVs/o5+uQbTjL3chynL4wXgUg2R9" +
                "q9UU8I5mEovUf86QZ7kOBIjJwqnzD1omageEHWwHdBO6B+dFabmdT9POxg==" +
                "-----END PUBLIC KEY-----";
        invalidEsPublicKeyString = "-----BEGIN PUBLIC KEY-----" +
                "HFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEVs/o5+uQbTjL3chynL4wXgUg2R9" +
                "q9UU8I5mEovUf86QZ7kOBIjJwqnzD1omageEHWwHdBO6B+dFabmdT9POxg==" +
                "-----END PUBLIC KEY-----";
        validRsX509CertString = "-----BEGIN CERTIFICATE-----" +
                "MIIDozCCAougAwIBAgIUIz2OdBS2tngLhKWhE7IBa4u/sucwDQYJKoZIhvcNAQEL" +
                "BQAwYTELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxEzARBgNVBAoM" +
                "ClRlc3RUZW5hbnQxEzARBgNVBAsMCnRlc3REZXZpY2UxEzARBgNVBAMMCnRlc3RE" +
                "ZXZpY2UwHhcNMjIxMTIyMTAzMjA5WhcNMjMxMTIyMTAzMjA5WjBhMQswCQYDVQQG" +
                "EwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTETMBEGA1UECgwKVGVzdFRlbmFudDET" +
                "MBEGA1UECwwKdGVzdERldmljZTETMBEGA1UEAwwKdGVzdERldmljZTCCASIwDQYJ" +
                "KoZIhvcNAQEBBQADggEPADCCAQoCggEBALtUlNS31SzxwqMzMR9jKOJYDhHj8zZt" +
                "LUYHi3s1en3wLdILp1Uy8O6Jy0Z66tPyM1u8lke0JK5gS+40yhJ+bvqioW8CnwbL" +
                "SLPmzGNmZKdfIJ08Si8aEtrRXMxpDyz4Is7JLnpjIIUZ4lmqC3MnoZHd6qhhJb1v" +
                "1Qy+QGlk4NJy1ZI0aPc/uNEUM7lWhPAJABZsWc6MN8flSWCnY8pJCdIk/cAktA0U" +
                "17tuvVduuFX/94763nWYikZIMJS/cTQMMVxYNMf1xcNNOVFlUSJHYHClk46QT9nT" +
                "8FWeFlgvvWhlXfhsp9aNAi3pX+KxIxqF2wABIAKnhlMa3CJW41323JsCAwEAAaNT" +
                "MFEwHQYDVR0OBBYEFGXDJW0X5pjcL3f7q2u6tqymrtLWMB8GA1UdIwQYMBaAFGXD" +
                "JW0X5pjcL3f7q2u6tqymrtLWMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQEL" +
                "BQADggEBAJIfCeen75uN9M6d/LsPI3wcNXgsBTB1cFH7r/dXHIh5FdvjfdhZswRh" +
                "0TRZAD1IvTKesEFfWVgEoHSxrtYe6zmcW9DC1wa172R6hmrRwozi0q4RhmNaHjP8" +
                "tJH02oLHeG531Ou6alP/F3+IIQPfXUM/Pb8wnSs0QNf5ktbGiXWX+tiRkeUIXjvL" +
                "Nlk29ooe8/AjRwDgx1GvfmKOanNGvmcLFGH8OSVhXoeYiqN9bSkWqFG6RjtB4uuR" +
                "CWb63S8029zu9whP08VJDUmEsnktkuSbyq9glKMSl5AZudUYuT7lK74K85cfWgq4" +
                "O5yBdEvM4CT4wUURvAmfQE0SRfSBfZE=" +
                "-----END CERTIFICATE-----";
        validEsX509CertString = "-----BEGIN CERTIFICATE-----" +
                "MIIB7TCCAZOgAwIBAgIUcNsOHPVdIIws+H7+XDC2IUBlajswCgYIKoZIzj0EAwIw" +
                "TDELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxEzARBgNVBAoMClRl" +
                "c3RUZW5hbnQxEzARBgNVBAMMCnRlc3REZXZpY2UwHhcNMjIxMTIzMTA1MTMxWhcN" +
                "MjMxMTIzMTA1MTMxWjBMMQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0" +
                "ZTETMBEGA1UECgwKVGVzdFRlbmFudDETMBEGA1UEAwwKdGVzdERldmljZTBZMBMG" +
                "ByqGSM49AgEGCCqGSM49AwEHA0IABBFbP6OfrkG04y93Icpy+MF4FINkfavVFPCO" +
                "ZhKL1H/OkGe5DgSIycKp8w9aJmoHhB1sB3QTugfnRWm5nU/TzsajUzBRMB0GA1Ud" +
                "DgQWBBSaqFjzps1qG+x2DPISjaXTWsTOdDAfBgNVHSMEGDAWgBSaqFjzps1qG+x2" +
                "DPISjaXTWsTOdDAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0gAMEUCIGYO" +
                "mhIkAOM+ZwYo9xrYoS3o1O0bGZjjc68Mu8yQJ7WNAiEAoKuRHHpa3+VKNs9pgMrI" +
                "p8Bx4c2nJOiK/RWLduzzPnM=" +
                "-----END CERTIFICATE-----";
        validRsJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTc5NjIzOTAyMn0." +
                "V6AcvZwoVVhZw69moZptpV3YifjBJE8LX0iIFmZE985FcBqWh0XupD9hINFwyi97qNZVp6KXuYdTddpRDT_dF6ZX1" +
                "Xx_k9k8Qq44LGvF2IDgd0DFPJ73DdEurVobsuxFm3x_Kf5-X3SRU0qY6un48iKkVF2dK0b_zv9Sa1hs2JD9jJCMql" +
                "OmM6YZqrstFkCsDklspFRB0rboar8q_ARC7MoQBWCVj9FpS5R0vnNwx3_JnZRGNPZrGC8wqYKHgE9iiTRT41V7fkM" +
                "FdhPW-9926q-_SKxeL0Kmsw2iOzSZtDXt_5R0Ecm7BBRfB_CQIh40Ax4T5kVHyp4vxXtZ_BLsVg";
        validEsJwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJpYXQiOjE1MzYyMzkwMjIsImV4cCI6MTkxNjIzOTAyMn0." +
                "Y3ZdIGB74PWhr-XEnv_NdYt7tfZjyB5IjrdVJcVPVfrpRG1TQODIIn04hIi83jm3FRa8CbjHlZ2w-DVN-SUZeQ";
        invalidEsJwt = "ayJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJpYXQiOjE1MzYyMzkwMjIsImV4cCI6MTkxNjIzOTAyMn0." +
                "Y3ZdIGB74PWhr-XEnv_NdYt7tfZjyB5IjrdVJcVPVfrpRG1TQODIIn04hIi83jm3FRa8CbjHlZ2w-DVN-SUZeQ";
        invalidAlgFieldRsJwt = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9." +
                "eyJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTkxNjIzOTAyMn0." +
                "ImvOzTOLztv0GHhcP3NG8oN6U0qWQN8ZzodwRUC7JlK0sdaZ9grYZIq4DA3hXbjd7tx-xFZtdNEfFRKIYRUOTPfiUftV249QzQ1rm" +
                "SUSBbd49Eu6QpcaIsz1CDXZcMFHscZrYepaZiTDSeUVkrp7Ci11GI7oavsJaSSVStIgH3xpnwgxi1NtZbVHCOesH8athEvaYdLmyK" +
                "3h53qlqBn7ujM_FGfZxe4Jx73FpHUtb9KVaNkTr1TNbDo6moC32N9oonoWCrew6hW6HJFsroYJg0sdB35xk1w8dWuFaBMMdEkSoON" +
                "bpRmttiWAQhglJayJCoCcpfet4g1refjgKXm1vw";
    }

    @BeforeEach
    void setUp() {
        authTokenValidator = new ExternalJwtAuthTokenValidator();
    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and valid x509 Certificate with RSA encrypted public
     * key is provided.
     */
    @Test
    public void testExpandValidJwtWithValidRsX509Certificate() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "RS256", validRsX509CertString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        final Jws<Claims> jws = authTokenValidator.expand(validRsJwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");
    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and valid X509 Certificate with EC encrypted public key
     * is provided.
     */
    @Test
    public void testExpandValidJwtWithValidEsX509Certificate() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "ES256", validEsX509CertString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        final Jws<Claims> jws = authTokenValidator.expand(validEsJwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("ES256");
    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and valid RSA encrypted public key is provided.
     */
    @Test
    public void testExpandValidJwtWithValidRsPublicKey() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "RS256", validRsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        final Jws<Claims> jws = authTokenValidator.expand(validRsJwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("RS256");

    }

    /**
     * Verifies that expand returns JWS Claims, when a valid JWT and valid EC encrypted public key is provided.
     */
    @Test
    public void testExpandValidJwtWithValidEsPublicKey() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "ES256", validEsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        final Jws<Claims> jws = authTokenValidator.expand(validEsJwt);
        assertThat(jws).isNotNull();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("ES256");

    }

    /**
     * Verifies that expand throws a {@link MalformedJwtException}, when an invalid JWT is provided.
     */
    @Test
    public void testExpandInvalidJwtWithValidEsPublicKey() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "ES256", validEsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        assertThrows(MalformedJwtException.class, () -> authTokenValidator.expand(invalidEsJwt));
    }

    /**
     * Verifies that expand throws a {@link RuntimeException}, when a valid JWT with a non-matching public key is
     * provided.
     */
    @Test
    public void testExpandValidJwtWithInvalidEsPublicKey() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "ES256", invalidEsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        assertThrows(RuntimeException.class, () -> authTokenValidator.expand(validEsJwt));
    }

    /**
     * Verifies that expand throws a {@link NullPointerException}, when no secret exists.
     */
    @Test
    public void testExpandNoExistingSecret() {

        assertThrows(NullPointerException.class, () -> authTokenValidator.expand(validEsJwt));
    }

    /**
     * Verifies that expand throws a {@link NullPointerException}, when no secret exists.
     */
    @Test
    public void testExpandNoExistingSecretWithSameAlgAsInJwtHeader() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "RS256", validRsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        assertThrows(NoSuchElementException.class, () -> authTokenValidator.expand(validEsJwt));
    }

    /**
     * Verifies that expand throws a {@link SignatureException}, when alg field in provided JWT is not one of the
     * supported types (RS256 and ES256).
     */
    @Test
    public void testExpandAlgFieldInJwtHeaderInvalid() {
        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "RS256", validRsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        assertThrows(SignatureException.class, () -> authTokenValidator.expand(invalidAlgFieldRsJwt));
    }

    /**
     * Verifies that expand throws a {@link IllegalArgumentException}, when the key in the secret has an invalid syntax.
     */
    @Test
    public void testExpandKeyInSecretHasInvalidSyntax() {
        final String invalidSyntaxRsPublicKeyString = validRsPublicKeyString.replace(CredentialsConstants.BEGIN_KEY, "")
                .replace(CredentialsConstants.END_KEY, "");

        authTokenValidator.setCredentialsObject(
                CredentialsObject.fromAsymmetricKey(deviceId, authId, "RS256", invalidSyntaxRsPublicKeyString,
                        Instant.ofEpochSecond(1516239022), Instant.ofEpochSecond(1796239022)));

        assertThrows(IllegalArgumentException.class, () -> authTokenValidator.expand(validRsJwt));
    }
}
