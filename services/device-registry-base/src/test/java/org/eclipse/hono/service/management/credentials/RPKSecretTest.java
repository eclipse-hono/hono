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

import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.Test;

/**
 * Verifies behavior of {@link RPKSecret}.
 */
class RPKSecretTest {

    private final String id = "id";
    private final String key = CredentialsConstants.BEGIN_KEY + "JWT" + CredentialsConstants.END_KEY;
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
     * Verifies that the key is correctly set, when a valid key is provided.
     */
    @Test
    void testSetKeyValidKey() {

        final RPKSecret secret = new RPKSecret();

        secret.setKey(key);
        assertThat(secret.getKey()).isEqualTo(key);
    }

    /**
     * Verifies that the setKey throws a {@link IllegalArgumentException}, when an invalid key is provided.
     */
    @Test
    void testSetKeyInvalidKey() {

        final RPKSecret secret = new RPKSecret();
        assertThrows(IllegalArgumentException.class, () -> secret.setKey("JWT"));
    }

    /**
     * Verifies that the algorithm and key of an existing secret are not merged into an updated secret if it contains a
     * new key.
     */
    @Test
    void testMergePropertiesUsesNewKey() {

        final String newKey = CredentialsConstants.BEGIN_KEY + "new-JWT" + CredentialsConstants.END_KEY;
        final RPKSecret updatedSecret = new RPKSecret();
        updatedSecret.setId(id);
        updatedSecret.setKey(newKey);

        final RPKSecret existingSecret = new RPKSecret();
        existingSecret.setId(id);
        existingSecret.setAlg(alg);
        existingSecret.setKey(key);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getAlg()).isNull();
        assertThat(updatedSecret.getKey()).isEqualTo(newKey);

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
        assertThat(updatedSecret.getKey()).isEqualTo(key);
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
                "RPKSecret{enabled=null, notBefore=null, notAfter=null, comment=null, key=%s, algorithm=%s}", key,
                alg));
    }
}
