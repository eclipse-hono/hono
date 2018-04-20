/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth.device;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

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

        UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD, false);

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

        UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that if username does not comply to the structure authId@tenantId, the created object for multi tenant mode is null.
     */
    @Test
    public void testTenantFromUserMultiTenantWrongUsernameStructure() {

        UsernamePasswordCredentials mqttUserNamePassword = UsernamePasswordCredentials.create("user/tenant", TEST_PASSWORD, false);
        assertNull(mqttUserNamePassword);
    }

    /**
     * Verifies that for single tenant mode, the tenant is automatically set to {@link Constants#DEFAULT_TENANT}.
     */
    @Test
    public void testTenantFromUserSingleTenant() {

        UsernamePasswordCredentials mqttUsernamePassword = UsernamePasswordCredentials.create(TEST_USER, TEST_PASSWORD, true);

        assertEquals(mqttUsernamePassword.getType(), CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        assertEquals(mqttUsernamePassword.getTenantId(), Constants.DEFAULT_TENANT);
        assertEquals(mqttUsernamePassword.getAuthId(), TEST_USER);
        assertEquals(mqttUsernamePassword.getPassword(), TEST_PASSWORD);
    }

    /**
     * Verifies that credentials can be successfully verified using the default hash function
     * (sha-256) if the secret on record does not explicitly specify a hash function.
     * 
     * @throws NoSuchAlgorithmException if the JVM does not support the default hash function (sha-256).
     */
    @Test
    public void testMatchesCredentialsUsesDefaultHashFunction() throws NoSuchAlgorithmException {

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
     * 
     * @throws NoSuchAlgorithmException if the JVM does not support sha-512.
     */
    @Test
    public void testMatchesCredentialsSucceedsForMatchingPassword() throws NoSuchAlgorithmException {

        // GIVEN a secret on record that uses sha-512 as the hash function
        final byte[] salt = "TheSalt".getBytes(StandardCharsets.UTF_8);
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecret(TEST_PASSWORD, "sha-512", null, null, salt);

        // WHEN a device provides matching credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, TEST_PASSWORD, false);

        // THEN verification of the credentials succeeds
        assertTrue(credentials.matchesCredentials(candidateSecret));
    }

    /**
     * Verifies that credentials are rejected if they do not match the secret on record.
     * 
     * @throws NoSuchAlgorithmException if the JVM does not support sha-512.
     */
    @Test
    public void testMatchesCredentialsFailsForNonMatchingPassword() throws NoSuchAlgorithmException {

        // GIVEN a secret on record that uses sha-512 as the hash function
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecret(TEST_PASSWORD, "sha-512", null, null, null);

        // WHEN a device provides non-matching credentials
        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(TEST_USER_OTHER_TENANT, "wrongpassword", false);

        // THEN verification of the credentials fails
        assertFalse(credentials.matchesCredentials(candidateSecret));
    }

    private String getHashedPassword(final String hashFunction, final byte[] salt, final String password) throws NoSuchAlgorithmException {
        return Base64.getEncoder().encodeToString(
                CredentialsObject.getHashedPassword(hashFunction, salt, password));
    }
}
