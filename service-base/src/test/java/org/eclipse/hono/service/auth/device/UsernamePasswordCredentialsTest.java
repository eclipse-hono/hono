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
package org.eclipse.hono.service.auth.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

/**
  * Tests verifying behavior of {@link UsernamePasswordCredentials}.
 */
public class UsernamePasswordCredentialsTest {

    private static final String TEST_USER                = "billie";
    private static final String TEST_OTHER_TENANT        = "OTHER_TENANT";
    private static final String TEST_USER_OTHER_TENANT   = TEST_USER + "@" + TEST_OTHER_TENANT;
    private static final String TEST_PASSWORD            = "hono";

    /**
     * Verifies that in multi tenant mode, a username containing userId@tenantId leads to a correctly filled instance.
     */
    @Test
    public void testTenantFromUserMultiTenant() {

        final UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD, false);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), TEST_OTHER_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }

    /**
     * Verifies that if no tenantId is present in the username, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsername() {

        final UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that if username does not comply to the structure authId@tenantId, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsernameStructure() {

        final UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create("user/tenant", TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that for single tenant mode, the tenant is automatically set to {@link Constants#DEFAULT_TENANT}.
     */
    @Test
    public void testTenantFromUserSingleTenant() {

        final UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD, true);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), Constants.DEFAULT_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }

    /**
     * Verifies that credentials can be successfully verified using the default hash function
     * (sha-256) if the secret on record does not explicitly specify a hash function.
     */
    @Test
    public void testMatchesCredentialsUsesDefaultHashFunction() {

        // GIVEN a secret on record that does not explicitly define a hash function
        final String hashedPassword = getHashedPassword(CredentialsConstants.DEFAULT_HASH_FUNCTION, null, TEST_PASSWORD);
        final JsonObject candidateSecret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, hashedPassword);

        // WHEN a device provides matching credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD, false);

        // THEN verification of the credentials succeeds
        assertTrue(credentials.matchesCredentials(candidateSecret));
    }

    /**
     * Verifies that credentials can be successfully verified using the hash function
     * specified for the secret.
     */
    @Test
    public void testMatchesCredentialsSucceedsForMatchingPassword() {

        // GIVEN a secret on record that uses sha-512 as the hash function
        final byte[] salt = "TheSalt".getBytes(StandardCharsets.UTF_8);
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecret(TEST_PASSWORD, "sha-512", null, null, salt);

        // WHEN a device provides matching credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD, false);

        // THEN verification of the credentials succeeds
        assertTrue(credentials.matchesCredentials(candidateSecret));
    }

    /**
     * Verifies that credentials support Bcrypt hash function.
     */
    @Test
    public void testMatchesCredentialsSucceedsForMatchingBCryptPassword() {

        // GIVEN a secret on record that uses the bcrypt hash function (with salt version 2a)
        // see https://www.dailycred.com/article/bcrypt-calculator
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecretForPasswordHash(
                "$2a$12$rcrgLZrGHkEMAMfP5atLGe4HOvI0ZdclQdWDt/6DfUcgNEphwK27i",
                "bcrypt",
                null,
                null,
                null);

        // WHEN a device provides matching credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, "kapua-password", false);

        // THEN verification of the credentials succeeds
        assertTrue(credentials.matchesCredentials(candidateSecret));
    }

    /**
     * Verifies that credentials are rejected if they do not match the secret on record.
     */
    @Test
    public void testMatchesCredentialsFailsForNonMatchingPassword() {

        // GIVEN a secret on record that uses sha-512 as the hash function
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecret(TEST_PASSWORD, "sha-512", Instant.now(), null, null);

        // WHEN a device provides non-matching credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, "wrongpassword", false);

        // THEN verification of the credentials fails
        assertFalse(credentials.matchesCredentials(candidateSecret));
    }

    /**
     * Verifies that credentials are rejected if the password hash on record contains
     * illegal Base64 characters.
     */
    @Test
    public void testMatchesCredentialsFailsForMalformedPwdHash() {

        // GIVEN a candidate secret that contains illegal characters in the password hash
        final JsonObject candidateSecret = new JsonObject()
                .put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "!NOT_BASE64!");

        // WHEN a device provides credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, "password", false);

        // THEN verification of the credentials fails but doesn't throw an exception
        assertFalse(credentials.matchesCredentials(candidateSecret));
    }

    /**
     * Verifies that credentials are rejected if the password salt on record contains
     * illegal Base64 characters.
     */
    @Test
    public void testMatchesCredentialsFailsForMalformedSalt() {

        // GIVEN a candidate secret that contains illegal characters in the password salt
        final JsonObject candidateSecret = new JsonObject()
                .put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "secret".getBytes(StandardCharsets.UTF_8))
                .put(CredentialsConstants.FIELD_SECRETS_SALT, "!NOT_BASE64!");

        // WHEN a device provides credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, "password", false);

        // THEN verification of the credentials fails but doesn't throw an exception
        assertFalse(credentials.matchesCredentials(candidateSecret));
    }

    private String getHashedPassword(final String hashFunction, final byte[] salt, final String password) {
        return CredentialsObject.getHashedPassword(hashFunction, salt, password);
    }
}
