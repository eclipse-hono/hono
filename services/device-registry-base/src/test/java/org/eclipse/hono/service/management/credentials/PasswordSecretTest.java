/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;


import static org.junit.jupiter.api.Assertions.assertEquals;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

/**
 * Verifies {@link PasswordSecret}.
 */
public class PasswordSecretTest {

    /**
     * Test encoding of a simple password secret.
     */
    @Test
    public void testEncodePasswordSecret() {

        final PasswordSecret secret = new PasswordSecret();
        CommonSecretTest.addCommonProperties(secret);

        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA256);
        secret.setPasswordHash("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f");
        secret.setSalt("abc");

        final JsonObject json = JsonObject.mapFrom(secret);
        CommonSecretTest.assertCommonProperties(json);
        assertEquals(CredentialsConstants.HASH_FUNCTION_SHA256, json.getString(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION));
        assertEquals("abc", json.getString(CredentialsConstants.FIELD_SECRETS_SALT));
        assertEquals("2a5d81942494986ce6e23aadfa18cd426a1d7ab90629a0814d244c4cd82cc81f",
                json.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
    }

    /**
     * Verifies that the password, hash function and salt of an existing secret
     * are not merged into an updated secret if it contains a new (plaintext) password.
     */
    @Test
    public void testMergePropertiesUsesNewPlaintextPassword() {

        final PasswordSecret updatedSecret = new PasswordSecret();
        updatedSecret.setId("one");
        updatedSecret.setPasswordPlain("new-pwd");

        final PasswordSecret existingSecret = new PasswordSecret();
        existingSecret.setId("one");
        existingSecret.setPasswordHash("hash-1");
        existingSecret.setSalt("salt-1");
        existingSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA512);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getPasswordPlain()).isEqualTo("new-pwd");
        assertThat(updatedSecret.getPasswordHash()).isNull();
        assertThat(updatedSecret.getHashFunction()).isNull();
        assertThat(updatedSecret.getSalt()).isNull();
    }

    /**
     * Verifies that the password, hash function and salt of an existing secret
     * are not merged into an updated secret if it contains a new (hashed) password.
     */
    @Test
    public void testMergePropertiesUsesNewHashedPassword() {

        final PasswordSecret updatedSecret = new PasswordSecret();
        updatedSecret.setId("one");
        updatedSecret.setPasswordHash("new-hash");
        updatedSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);

        final PasswordSecret existingSecret = new PasswordSecret();
        existingSecret.setId("one");
        existingSecret.setPasswordHash("hash-1");
        existingSecret.setSalt("salt-1");
        existingSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA512);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getPasswordHash()).isEqualTo("new-hash");
        assertThat(updatedSecret.getPasswordPlain()).isNull();
        assertThat(updatedSecret.getHashFunction()).isEqualTo(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        assertThat(updatedSecret.getSalt()).isNull();
    }

    /**
     * Verifies that the password, hash function and salt of an existing secret
     * are merged into an updated secret if it contains an ID only.
     */
    @Test
    public void testMergePropertiesUsesExistingHashedPassword() {

        final PasswordSecret updatedSecret = new PasswordSecret();
        updatedSecret.setId("one");

        final PasswordSecret existingSecret = new PasswordSecret();
        existingSecret.setId("one");
        existingSecret.setPasswordHash("hash-1");
        existingSecret.setSalt("salt-1");
        existingSecret.setHashFunction(CredentialsConstants.HASH_FUNCTION_SHA512);

        updatedSecret.merge(existingSecret);

        assertThat(updatedSecret.getPasswordHash()).isEqualTo("hash-1");
        assertThat(updatedSecret.getPasswordPlain()).isNull();
        assertThat(updatedSecret.getHashFunction()).isEqualTo(CredentialsConstants.HASH_FUNCTION_SHA512);
        assertThat(updatedSecret.getSalt()).isEqualTo("salt-1");
    }
}
