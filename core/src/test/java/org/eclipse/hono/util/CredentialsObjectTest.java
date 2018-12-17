/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Base64;


/**
 * Verifies behavior of {@link CredentialsObject}.
 *
 */
public class CredentialsObjectTest {

    /**
     * Verifies that credentials that do not contain any secrets are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMissingSecrets() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contains an empty set of secrets only are
     * considered valid.
     */
    @Test
    public void testCheckSecretsAcceptsEmptySecrets() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.addSecret(new JsonObject());
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contain a malformed not-before value are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMalformedNotBefore() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        creds.addSecret(new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "malformed"));
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contain a malformed not-before value are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMalformedNotAfter() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        creds.addSecret(new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "malformed"));
        creds.checkSecrets();
    }

    /**
     * Verifies that credentials that contain inconsistent values for not-before
     * and not-after are detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsInconsistentValidityPeriod() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
        creds.addSecret(new JsonObject()
                .put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2018-10-10T00:00:00+00:00")
                .put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2018-10-01T00:00:00+00:00"));
        creds.checkSecrets();
    }

    /**
     * Verifies that hashed-password credentials that do not contain the hash function name are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMissingHashFunction() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        creds.addSecret(new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "hash"));
        creds.checkSecrets();
    }

    /**
     * Verifies that hashed-password credentials that do not contain the hash value are
     * detected as invalid.
     */
    @Test(expected = IllegalStateException.class)
    public void testCheckSecretsDetectsMissingHashValue() {
        final CredentialsObject creds = new CredentialsObject("tenant", "device", "x509-cert");
        creds.setType(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        creds.addSecret(new JsonObject().put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, CredentialsConstants.HASH_FUNCTION_SHA256));
        creds.checkSecrets();
    }

    /**
     * Verifies that a secret with an already hashed password remains unchanged.
     */
    @Test
    public void alreadyHashedSecretsAreUnchanged() {
        final JsonObject expected = new JsonObject();
        expected.put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, CredentialsConstants.HASH_FUNCTION_BCRYPT);
        expected.put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "abc");

        final JsonObject actual = CredentialsObject.hashPwdAndUpdateSecret(expected.copy(), "doesnotmatter");

        Assert.assertEquals(expected, actual);
    }

    /**
     * Verifies that the secret is correctly updated when hashed with bcrypt.
     */
    @Test
    @Ignore
    // Fails because Apache logger not found
    public void secretIsUpdatedForBcrypt() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_BCRYPT;

        final JsonObject secret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "mylittlesecret");

        CredentialsObject.hashPwdAndUpdateSecret(secret, hashFunction);

        Assert.assertNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN));
        Assert.assertNotNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));

        // the pwd hash contains the salt for bcrypt
        Assert.assertNull(secret.getString(CredentialsConstants.FIELD_SECRETS_SALT));

        Assert.assertEquals(hashFunction, secret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));
    }

    /**
     * Verifies that the secret is correctly updated when hashed with sha.
     */
    @Test
    public void secretIsUpdatedForSha() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_SHA256;

        final JsonObject secret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "mylittlesecret");

        CredentialsObject.hashPwdAndUpdateSecret(secret, hashFunction);

        Assert.assertNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN));
        Assert.assertNotNull(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
        Assert.assertNotNull(secret.getString(CredentialsConstants.FIELD_SECRETS_SALT));

        Assert.assertEquals(hashFunction, secret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));
    }

    /**
     * Verifies that a if an unknown hash function is configured, an error is produced.
     */
    @Test(expected = IllegalArgumentException.class)
    public void unknownHashFunctionIsGiven() {
        final String hashFunction = "bumlux";

        final JsonObject secret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "mylittlesecret");

        CredentialsObject.hashPwdAndUpdateSecret(secret, hashFunction);
    }

    /**
     * Verifies that the plain password inside the secret is correctly hashed with bcrypt.
     */
    @Test
    @Ignore
    // Fails because Apache logger not found
    public void hashIsAppliedForBcrypt() {

        final JsonObject secret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "mylittlesecret");

        CredentialsObject.hashPwdAndUpdateSecret(secret, CredentialsConstants.HASH_FUNCTION_BCRYPT);

        Assert.assertTrue(new BCryptPasswordEncoder(10).matches("mylittlesecret",
                secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH)));

    }

    /**
     * Verifies that the plain password inside the secret is correctly hashed with sha-256.
     */
    @Test
    public void hashIsAppliedForSha256() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_SHA256;

        final JsonObject secretActual = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN,
                "mylittlesecret");

        CredentialsObject.hashPwdAndUpdateSecret(secretActual, hashFunction);

        final byte[] salt = Base64.getDecoder().decode(secretActual.getString(CredentialsConstants.FIELD_SECRETS_SALT));

        final String hashExpected = ClearTextPassword.encode(hashFunction, salt, "mylittlesecret");

        Assert.assertEquals(hashExpected, secretActual.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
    }

    /**
     * Verifies that the plain password inside the secret is correctly hashed with sha-512.
     */
    @Test
    public void hashIsAppliedForSha512() {
        final String hashFunction = CredentialsConstants.HASH_FUNCTION_SHA512;
        final JsonObject secretActual = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN,
                "mylittlesecret");

        CredentialsObject.hashPwdAndUpdateSecret(secretActual, hashFunction);

        final byte[] salt = Base64.getDecoder().decode(secretActual.getString(CredentialsConstants.FIELD_SECRETS_SALT));

        final String hashExpected = ClearTextPassword.encode(hashFunction, salt, "mylittlesecret");

        Assert.assertEquals(hashExpected, secretActual.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH));
    }
}
