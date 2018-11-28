/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.credentials;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.eclipse.hono.service.credentials.CredentialsPlainPasswordHelper.hashPwdAndUpdateSecret;
import static org.eclipse.hono.service.credentials.CredentialsPlainPasswordHelper.hashWithSha;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link CredentialsPlainPasswordHelper}.
 */
public class CredentialsPlainPasswordHelperTest {

    private static final String PASSWORD = "mylittlesecret";
    private static final byte[] SALT = "saltnpepper".getBytes(StandardCharsets.UTF_8);

    /**
     * Tests that password is correctly hashed with sha-256.
     */
    @Test
    public void testHashWithSha256() {

        final String hash = hashWithSha(CredentialsConstants.HASH_FUNCTION_SHA256, PASSWORD, SALT);

        assertEquals("xj4mcF0CA/kkdgRuVciXZoKJsM09uS1/6VaeowMfvaQ=", hash);
    }

    /**
     * Tests that password is correctly hashed with sha-256.
     */
    @Test
    public void testHashWithSha512() {
        final String hash = hashWithSha(CredentialsConstants.HASH_FUNCTION_SHA512, PASSWORD, SALT);

        assertEquals("yENeU2Brk8BVZrsuraeIo2v6TZWOadn1HnvNZlNMD5M6wcZKz7eF5DfZV0/0w+8t9n/ocBFyLnUU8Yn2eCnfxA==",
                hash);
    }

    /**
     * Tests that a secret with an already hashed password remains unchanged.
     */
    @Test
    public void alreadyHashedSecretsAreUnchanged() {
        final JsonObject expected = new JsonObject();
        expected.put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, CredentialsConstants.HASH_FUNCTION_BCRYPT);
        expected.put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "abc");

        final JsonObject actual = hashPwdAndUpdateSecret(expected.copy());

        assertEquals(expected, actual);
    }

    /**
     * Tests that the secret is correctly updated when hashed with bcrypt.
     */
    @Test
    public void secretIsUpdatedForBcrypt() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_BCRYPT;

        final JsonObject secret = newPlainPwdSecret(hashFunction);

        hashPwdAndUpdateSecret(secret);

        assertNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN));
        assertNotNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        assertNull(secret.getString(CredentialsConstants.FIELD_SECRETS_SALT));

        assertEquals(hashFunction, secret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));
    }

    /**
     * Tests that the secret is correctly updated when hashed with sha.
     */
    @Test
    public void secretIsUpdatedForSha() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_SHA256;

        final JsonObject secret = newPlainPwdSecret(hashFunction);

        hashPwdAndUpdateSecret(secret);

        assertNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN));
        assertNotNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        assertNotNull(secret.getString(CredentialsConstants.FIELD_SECRETS_SALT));

        assertEquals(hashFunction, secret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));
    }

    /**
     * Tests that a if no hashing function is provided in secret, then bcrypt is used.
     */
    @Test
    public void noHashFunctionIsGiven() {

        final JsonObject secret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, PASSWORD);

        hashPwdAndUpdateSecret(secret);

        assertTrue(new BCryptPasswordEncoder(10).matches(PASSWORD,
                secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH)));
    }

    /**
     * Tests that the plain password inside the secret is correctly hashed with bcrypt.
     */
    @Test
    public void hashIsAppliedForBcrypt() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_BCRYPT;

        final JsonObject secret = newPlainPwdSecret(hashFunction);

        hashPwdAndUpdateSecret(secret);

        assertTrue(new BCryptPasswordEncoder(10).matches(PASSWORD,
                secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH)));

    }

    /**
     * Tests that the plain password inside the secret is correctly hashed with sha-256.
     */
    @Test
    public void hashIsAppliedForSha256() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_SHA256;

        final JsonObject secretActual = newPlainPwdSecret(hashFunction);

        hashPwdAndUpdateSecret(secretActual);

        final String salt = secretActual.getString(CredentialsConstants.FIELD_SECRETS_SALT);

        final String hashExpected = hashWithSha(hashFunction, PASSWORD, Base64.getDecoder().decode(salt));

        assertEquals(hashExpected, secretActual.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
    }

    /**
     * Tests that the plain password inside the secret is correctly hashed with sha-512.
     */
    @Test
    public void hashIsAppliedForSha512() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_SHA512;

        final JsonObject secretActual = newPlainPwdSecret(hashFunction);

        hashPwdAndUpdateSecret(secretActual);

        final String salt = secretActual.getString(CredentialsConstants.FIELD_SECRETS_SALT);

        final String hashExpected = hashWithSha(hashFunction, PASSWORD, Base64.getDecoder().decode(salt));

        assertEquals(hashExpected, secretActual.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
    }

    private JsonObject newPlainPwdSecret(final String hashFunction) {
        return new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, PASSWORD)
                .put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, hashFunction);
    }

}
