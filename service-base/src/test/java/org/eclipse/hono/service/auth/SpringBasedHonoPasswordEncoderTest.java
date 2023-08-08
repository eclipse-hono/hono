/**
 * Copyright (c) 2018, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;


/**
 * Verifies behavior of {@link SpringBasedHonoPasswordEncoder}.
 *
 */
public class SpringBasedHonoPasswordEncoderTest {

    private static final String TEST_PASSWORD = "password";

    private SpringBasedHonoPasswordEncoder encoder;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        encoder = new SpringBasedHonoPasswordEncoder(4);
    }

    /**
     * Verifies that the encoder uses the default hash function
     * if credentials on record do not specify a hash function.
     */
    @Test
    public void testMatchesFallsBackToDefaultHashFunction() {

        // GIVEN a hashed password on record that uses the default hash function but does not explicitly define a hash function
        final String hashedPassword = getPasswordHash(CredentialsConstants.DEFAULT_HASH_FUNCTION, null, TEST_PASSWORD);
        final JsonObject candidateSecret = new JsonObject().put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, hashedPassword);

        // WHEN the password is matched against the candidate secret
        final String pwd = TEST_PASSWORD;

        // THEN verification succeeds
        assertTrue(encoder.matches(pwd, candidateSecret));
    }

    /**
     * Verifies that credentials can be successfully verified using the SHA based hash function
     * specified for the secret.
     */
    @Test
    public void testMatchesSucceedsForCorrectPassword() {

        // GIVEN a secret on record that uses sha-512 as the hash function
        final byte[] salt = "TheSalt".getBytes(StandardCharsets.UTF_8);
        final String hashedPassword = getPasswordHash(CredentialsConstants.HASH_FUNCTION_SHA512, salt, TEST_PASSWORD);
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecretForPasswordHash(
                hashedPassword,
                CredentialsConstants.HASH_FUNCTION_SHA512,
                null, null,
                salt);

        // WHEN a device provides matching credentials
        final String pwd = TEST_PASSWORD;

        // THEN verification of the credentials succeeds
        assertTrue(encoder.matches(pwd, candidateSecret));
    }

    /**
     * Verifies that credentials can be successfully verified using the Bcrypt hash function.
     */
    @Test
    public void testMatchesSucceedsForMatchingBCryptPassword() {

        // GIVEN a hashed password on record that uses the bcrypt hash function (with salt version 2a)
        // see https://www.dailycred.com/article/bcrypt-calculator
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecretForPasswordHash(
                "$2a$12$rcrgLZrGHkEMAMfP5atLGe4HOvI0ZdclQdWDt/6DfUcgNEphwK27i",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null,
                null,
                (byte[]) null);

        // WHEN a device provides matching credentials
        final String pwd = "kapua-password";

        // THEN verification of the credentials succeeds
        assertTrue(encoder.matches(pwd, candidateSecret));
    }

    /**
     * Verifies that credentials are rejected if they do not match the secret on record.
     */
    @Test
    public void testMatchesCredentialsFailsForNonMatchingPassword() {

        // GIVEN a secret on record that uses sha-512 as the hash function
        final String hashedPassword = getPasswordHash(CredentialsConstants.HASH_FUNCTION_SHA512, null, TEST_PASSWORD);
        final JsonObject candidateSecret = CredentialsObject.hashedPasswordSecretForPasswordHash(
                hashedPassword,
                CredentialsConstants.HASH_FUNCTION_SHA512,
                Instant.now(),
                null,
                (byte[]) null);

        // WHEN a device provides non-matching credentials
        final String pwd = "wrongpassword";

        // THEN verification of the credentials fails
        assertFalse(encoder.matches(pwd, candidateSecret));
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
        final String pwd = TEST_PASSWORD;

        // THEN verification of the credentials fails but doesn't throw an exception
        assertFalse(encoder.matches(pwd, candidateSecret));
    }

    /**
     * Verifies that credentials are rejected if the password salt on record contains
     * illegal Base64 characters.
     */
    @Test
    public void testMatchesCredentialsFailsForMalformedSalt() {

        // GIVEN a candidate secret that contains illegal characters in the password salt
        final String hashedPassword = getPasswordHash(CredentialsConstants.DEFAULT_HASH_FUNCTION, null, TEST_PASSWORD);
        final JsonObject candidateSecret = new JsonObject()
                .put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, hashedPassword)
                .put(CredentialsConstants.FIELD_SECRETS_SALT, "!NOT_BASE64!");

        // WHEN a device provides credentials
        final String pwd = TEST_PASSWORD;

        // THEN verification of the credentials fails but doesn't throw an exception
        assertFalse(encoder.matches(pwd, candidateSecret));
    }


    private String getPasswordHash(final String hashFunction, final byte[] salt, final String pwd) {

        try {
            final MessageDigest digest = MessageDigest.getInstance(hashFunction);
            if (salt != null) {
                digest.update(salt);
            }
            return Base64.getEncoder().encodeToString(digest.digest(pwd.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            fail();
            return null;
        }
    }
}
