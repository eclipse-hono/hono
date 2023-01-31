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

import java.util.Arrays;
import java.util.Base64;

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link RPKSecret}.
 */
class RPKSecretTest {

    private final String id = "id";
    private final String key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAu1SU1LfVLPHCozMxH2Mo" +
            "4lgOEePzNm0tRgeLezV6ffAt0gunVTLw7onLRnrq0/IzW7yWR7QkrmBL7jTKEn5u" +
            "+qKhbwKfBstIs+bMY2Zkp18gnTxKLxoS2tFczGkPLPgizskuemMghRniWaoLcyeh" +
            "kd3qqGElvW/VDL5AaWTg0nLVkjRo9z+40RQzuVaE8AkAFmxZzow3x+VJYKdjykkJ" +
            "0iT9wCS0DRTXu269V264Vf/3jvredZiKRkgwlL9xNAwxXFg0x/XFw005UWVRIkdg" +
            "cKWTjpBP2dPwVZ4WWC+9aGVd+Gyn1o0CLelf4rEjGoXbAAEgAqeGUxrcIlbjXfbc" +
            "mwIDAQAB";

    private final String cert = "MIIB7zCCAZWgAwIBAgIUZcrDSer0tQHRYK/jqQtZ3dSl47swCgYIKoZIzj0EAwIw" +
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
    private final String alg = CredentialsConstants.EC_ALG;

    /**
     * Verifies that the algorithm is correctly set, when a valid algorithm is provided.
     */
    @Test
    void testSetAlgValidAlgorithm() {

        final String algRS = CredentialsConstants.RSA_ALG;
        final RPKSecret secret = new RPKSecret();
        secret.setAlg(algRS);
        assertThat(secret.getAlg()).isEqualTo(algRS);
    }

    /**
     * Verifies that the setAlg throws a {@link IllegalArgumentException}, when an invalid algorithm is provided.
     */
    @Test
    void testSetAlgInvalidAlgorithm() {

        final RPKSecret secret = new RPKSecret();
        assertThrows(IllegalArgumentException.class, () -> secret.setAlg("Invalid"));
    }

    /**
     * Verifies that the key is correctly set, when a valid raw public key is provided.
     */
    @Test
    void testSetKeyValidRawPublicKey() {

        final RPKSecret secret = new RPKSecret();

        secret.setKey(key);
        final byte[] keyBytes = Base64.getDecoder().decode(key);
        assertThat(secret.getKey()).isEqualTo(keyBytes);
    }

    /**
     * Verifies that the key is correctly set, when a valid certificate is provided.
     */
    @Test
    void testSetKeyValidCertificate() {

        final RPKSecret secret = new RPKSecret();

        secret.setKey(cert);
        assertThat(secret.getKey()).isNotEqualTo(null);
    }

    /**
     * Verifies that the setKey throws a {@link IllegalArgumentException}, when an invalid key is provided.
     */
    @Test
    void testSetKeyInvalidKey() {

        final RPKSecret secret = new RPKSecret();
        assertThrows(RuntimeException.class, () -> secret.setKey("JWT"));
    }

    /**
     * Verifies that the algorithm and key of an existing secret are not merged into an updated secret if it contains a
     * new key.
     */
    @Test
    void testMergePropertiesUsesNewKey() {
        final RPKSecret updatedSecret = new RPKSecret();
        updatedSecret.setId(id);
        updatedSecret.setKey(key);

        final RPKSecret existingSecret = new RPKSecret();
        existingSecret.setId(id);
        existingSecret.setAlg(alg);
        existingSecret.setKey(cert);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getAlg()).isNull();
        assertThat(Base64.getEncoder().encodeToString(updatedSecret.getKey())).isEqualTo(key);

    }

    /**
     * Verifies that the algorithm and key of an existing secret are merged into an updated secret if it contains an ID
     * only.
     */
    @Test
    void testMergePropertiesUsesExistingKey() {

        final RPKSecret updatedSecret = new RPKSecret();
        updatedSecret.setId(id);

        final RPKSecret existingSecret = new RPKSecret();
        existingSecret.setId(id);
        existingSecret.setAlg(alg);
        existingSecret.setKey(key);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getAlg()).isEqualTo(alg);
        assertThat(Base64.getEncoder().encodeToString(updatedSecret.getKey())).isEqualTo(key);
    }

    /**
     * Verifies toStringHelper functionality.
     */
    @Test
    void testToStringHelper() {

        final RPKSecret secret = new RPKSecret();
        secret.setId(id);
        secret.setAlg(alg);
        secret.setKey(key);

        final String outputString = secret.toStringHelper().toString();

        assertThat(outputString).isEqualTo(String.format(
                "RPKSecret{enabled=null, notBefore=null, notAfter=null, comment=null, key=%s, algorithm=%s}", Arrays.toString(Base64.getDecoder().decode(key)),
                alg));
    }
}
