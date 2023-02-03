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

package org.eclipse.hono.service.management.credentials;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Base64;

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link RpkSecret}.
 */
class RpkSecretTest {

    private final String id = "id";
    private final byte[] key = Base64.getDecoder()
            .decode("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo" +
                    "4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u" +
                    "+qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh" +
                    "kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ" +
                    "0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg" +
                    "cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc" +
                    "mwIDAQAB");
    private final String alg = CredentialsConstants.RSA_ALG;

    /**
     * Verifies that the key and algorithm are correctly set, when a valid raw public key is provided.
     */
    @Test
    void testSetKeyValidRawPublicKey() {
        final RpkSecret secret = new RpkSecret();
        secret.setKey(key);
        assertThat(secret.getKey()).isEqualTo(key);
        assertThat(secret.getAlgorithm()).isEqualTo(CredentialsConstants.RSA_ALG);
    }

    /**
     * Verifies that the key and algorithm are correctly set, when a valid x509 Certificate with RSA encrypted public
     * key is provided.
     */
    @Test
    void testSetCertificateValidRsaX509Certificate() throws CertificateException {
        final RpkSecret secret = new RpkSecret();

        final byte[] validRsaX509CertString = Base64.getDecoder()
                .decode("MIIDezCCAmOgAwIBAgIUWAQ3roUv7ojy7Mz6Cp4gl1Fg2i0wDQYJKoZIhvcNAQEL" +
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
                        "U3JGN02ABUy2tcU4rdyoZ0VAnvhPOGCfMrYD6dnpbg==");
        secret.setCertificate(validRsaX509CertString);
        assertThat(secret.getKey()).isNotNull();
        assertThat(secret.getAlgorithm()).isEqualTo(CredentialsConstants.RSA_ALG);
    }

    /**
     * Verifies that the key and algorithm are correctly set, when a valid x509 Certificate with EC encrypted public key
     * is provided.
     */
    @Test
    void testSetCertificateValidEcX509Certificate() throws CertificateException {
        final RpkSecret secret = new RpkSecret();

        final byte[] validEcX509CertString = Base64.getDecoder()
                .decode("MIIB7zCCAZWgAwIBAgIUZcrDSer0tQHRYK/jqQtZ3dSl47swCgYIKoZIzj0EAwIw" +
                        "TDELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxEzARBgNVBAoMClRl" +
                        "c3RUZW5hbnQxEzARBgNVBAsMClRlc3REZXZpY2UwIBcNMjIxMjIyMTM0MDEwWhgP" +
                        "MjA1MDA1MDgxMzQwMTBaMEwxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0" +
                        "YXRlMRMwEQYDVQQKDApUZXN0VGVuYW50MRMwEQYDVQQLDApUZXN0RGV2aWNlMFkw" +
                        "EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEVs/o5+uQbTjL3chynL4wXgUg2R9q9UU" +
                        "8I5mEovUf86QZ7kOBIjJwqnzD1omageEHWwHdBO6B+dFabmdT9POxqNTMFEwHQYD" +
                        "VR0OBBYEFJqoWPOmzWob7HYM8hKNpdNaxM50MB8GA1UdIwQYMBaAFJqoWPOmzWob" +
                        "7HYM8hKNpdNaxM50MA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIg" +
                        "Nb6jSmGvzPlpzRyboQKdBPiIR2hHgf1e3SBJngoubeQCIQCsZi+kFmtNCU6AELwz" +
                        "UYEP5eqNVbGBJqnmKx5vx1KtEg==");
        secret.setCertificate(validEcX509CertString);
        assertThat(secret.getKey()).isNotNull();
        assertThat(secret.getAlgorithm()).isEqualTo(CredentialsConstants.EC_ALG);
    }

    /**
     * Verifies that the setKey throws a {@link IllegalArgumentException}, when an invalid key is provided.
     */
    @Test
    void testSetKeyInvalidKey() {

        final RpkSecret secret = new RpkSecret();
        final byte[] invalidKey = Base64.getDecoder().decode("invalid");
        assertThrows(RuntimeException.class, () -> secret.setKey(invalidKey));
    }

    /**
     * Verifies that the setCertificate throws a {@link CertificateException}, when an invalid certificate is provided.
     */
    @Test
    void testSetCertificateInvalidCertificate() {

        final RpkSecret secret = new RpkSecret();
        assertThrows(CertificateException.class, () -> secret.setCertificate(Base64.getDecoder().decode("invalid")));
    }

    /**
     * Verifies that the algorithm and key of an existing secret are not merged into an updated secret if it contains a
     * new key.
     */
    @Test
    void testMergePropertiesUsesNewKey() {
        final RpkSecret updatedSecret = new RpkSecret();

        final byte[] newKey = Base64.getDecoder().decode(
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEr+JOt8/aIx2QpV4/ThlRMUHeSZBDdSIrlwv7e" +
                        "T0u4m/FZSuwrxddB7lnE17pwLxctD7zbnD90eD1jjuOGSg4Zw==");

        updatedSecret.setId(id);
        updatedSecret.setKey(newKey);

        final RpkSecret existingSecret = new RpkSecret();
        existingSecret.setId(id);
        existingSecret.setKey(key);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getKey()).isEqualTo(newKey);

    }

    /**
     * Verifies that the algorithm and key of an existing secret are merged into an updated secret if it contains an ID
     * only.
     */
    @Test
    void testMergePropertiesUsesExistingKey() {

        final RpkSecret updatedSecret = new RpkSecret();
        updatedSecret.setId(id);

        final RpkSecret existingSecret = new RpkSecret();
        existingSecret.setId(id);
        existingSecret.setAlgorithm(alg);
        existingSecret.setKey(key);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getAlgorithm()).isEqualTo(alg);
        assertThat(updatedSecret.getKey()).isEqualTo(key);
    }

    /**
     * Verifies toStringHelper functionality.
     */
    @Test
    void testToStringHelper() {

        final RpkSecret secret = new RpkSecret();
        secret.setId(id);
        secret.setAlgorithm(alg);
        secret.setKey(key);

        final String outputString = secret.toStringHelper().toString();

        assertThat(outputString).isEqualTo(String.format(
                "RpkSecret{enabled=null, notBefore=null, notAfter=null, comment=null, key=%s, algorithm=%s}",
                Arrays.toString(key), alg));
    }
}
