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

package org.eclipse.hono.util;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Verifies behavior of {@link ClearTextPassword}.
 */
public class ClearTextPasswordTest {

    /**
     * Verifies that the password is correctly hashed with bcrypt.
     */
    @Test
    @Ignore
    // Fails because Apache logger not found
    public void hashIsAppliedForBcrypt() {
        final String hash = ClearTextPassword.encodeBCrypt("mylittlesecret");
        Assert.assertTrue(new BCryptPasswordEncoder(10).matches("mylittlesecret", hash));
    }

    /**
     * Verifies that {@code null} is returned for hash function "bcrypt".
     */
    @Test
    public void randomSaltForBcrypt() {
        Assert.assertNull(ClearTextPassword.randomSaltFor("bcrypt"));
    }

    /**
     * Verifies that the salt is strong enough for hash function "sha-256".
     */
    @Test
    public void randomSaltForSha256() {
        final byte[] salt = ClearTextPassword.randomSaltFor("sha-256");

        Assert.assertEquals(32, salt.length);
        Assert.assertNotEquals(salt, ClearTextPassword.randomSaltFor("sha-256"));
    }

    /**
     * Verifies that the salt is strong enough for hash function "sha-512".
     */
    @Test
    public void randomSaltForSha512() {
        final byte[] salt = ClearTextPassword.randomSaltFor("sha-512");

        Assert.assertEquals(64, salt.length);
        Assert.assertNotEquals(salt, ClearTextPassword.randomSaltFor("sha-512"));
    }

    /**
     * Verifies that exception is thrown for unknown hash function.
     */
    @Test(expected = IllegalArgumentException.class)
    public void randomSaltFor_unknown() {
        ClearTextPassword.randomSaltFor("bumlux");
    }

    /**
     * Verifies that exception is thrown if hash function is {@code null}.
     */
    @Test(expected = NullPointerException.class)
    public void randomSaltFor_null() {
        ClearTextPassword.randomSaltFor(null);
    }

    /**
     * Verifies that password is correctly hashed with sha-256.
     */
    @Test
    public void testHashWithSha256() {

        final String hash = ClearTextPassword.encode(CredentialsConstants.HASH_FUNCTION_SHA256,
                "saltnpepper".getBytes(StandardCharsets.UTF_8), "mylittlesecret");

        Assert.assertEquals("xj4mcF0CA/kkdgRuVciXZoKJsM09uS1/6VaeowMfvaQ=", hash);
    }

    /**
     * Verifies that password is correctly hashed with sha-256.
     */
    @Test
    public void testHashWithSha512() {
        final String hash = ClearTextPassword.encode(CredentialsConstants.HASH_FUNCTION_SHA512,
                "saltnpepper".getBytes(StandardCharsets.UTF_8), "mylittlesecret");

        Assert.assertEquals("yENeU2Brk8BVZrsuraeIo2v6TZWOadn1HnvNZlNMD5M6wcZKz7eF5DfZV0/0w+8t9n/ocBFyLnUU8Yn2eCnfxA==",
                hash);
    }
}