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
 * Verifies behavior of {@link AsymmetricKeySecret}.
 */
class AsymmetricKeySecretTest {

    private final String id = "id";
    private final String key = CredentialsConstants.BEGIN_KEY + "JWT" + CredentialsConstants.END_KEY;
    private final String alg = "ES256";

    /**
     * Verifies that the algorithm is correctly set, when a valid algorithm is provided.
     */
    @Test
    public void testSetAlgValidAlgorithm() {

        final String algRS = "RS256";
        final AsymmetricKeySecret secret = new AsymmetricKeySecret();
        secret.setAlg(algRS);
        assertThat(secret.getAlg()).isEqualTo(algRS);
    }

    /**
     * Verifies that the setAlg throws a {@link IllegalArgumentException}, when an invalid algorithm is provided.
     */
    @Test
    public void testSetAlgInvalidAlgorithm() {

        final AsymmetricKeySecret secret = new AsymmetricKeySecret();
        assertThrows(IllegalArgumentException.class, () -> secret.setAlg("Invalid"));
    }

    /**
     * Verifies that the key is correctly set, when a valid key is provided.
     */
    @Test
    public void testSetKeyValidKey() {

        final AsymmetricKeySecret secret = new AsymmetricKeySecret();

        secret.setKey(key);
        assertThat(secret.getKey()).isEqualTo(key);
    }

    /**
     * Verifies that the setKey throws a {@link IllegalArgumentException}, when an invalid key is provided.
     */
    @Test
    public void testSetKeyInvalidKey() {

        final AsymmetricKeySecret secret = new AsymmetricKeySecret();
        assertThrows(IllegalArgumentException.class, () -> secret.setKey("JWT"));
    }

    /**
     * Verifies that the algorithm and key of an existing secret are not merged into an updated secret if it contains a
     * new key.
     */
    @Test
    public void testMergePropertiesUsesNewKey() {

        final String newKey = CredentialsConstants.BEGIN_KEY + "new-JWT" + CredentialsConstants.END_KEY;
        final AsymmetricKeySecret updatedSecret = new AsymmetricKeySecret();
        updatedSecret.setId(id);
        updatedSecret.setKey(newKey);

        final AsymmetricKeySecret existingSecret = new AsymmetricKeySecret();
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
    public void testMergePropertiesUsesExistingKey() {

        final AsymmetricKeySecret updatedSecret = new AsymmetricKeySecret();
        updatedSecret.setId(id);

        final AsymmetricKeySecret existingSecret = new AsymmetricKeySecret();
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
    public void testToStringHelper() {

        final AsymmetricKeySecret secret = new AsymmetricKeySecret();
        secret.setId(id);
        secret.setAlg(alg);
        secret.setKey(key);

        final String outputString = secret.toStringHelper().toString();

        assertThat(outputString).isEqualTo(String.format(
                "AsymmetricKeySecret{enabled=null, notBefore=null, notAfter=null, comment=null, key=%s, alg=%s}", key,
                alg));
    }
}
