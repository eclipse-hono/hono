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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Clear text representation of the password. It's used to parse Strings in the form of {Base64(salt)}password into the value object, like
 *
 * <pre>
 * ClearTextPassword password = new ClearTextPassword("{VGhlU2FsdA==}ThePassword");
 * System.out.println(password.salt + " " + password.password);
 * </pre>
 *
 * You can also use it to encode the values, like
 *
 * <pre>
 * ClearTextPassword.encode("sha-256", "TheSalt".getBytes(StandardCharsets.UTF_8), "ThePassword");
 * </pre>
 */
public class ClearTextPassword extends BasePassword {

    /**
     * The cryptographically maximal strong salt for SHA-256 in bytes.
     */
    private static final int SALT_LENGTH_SHA256 = 32;
    /**
     * The cryptographically maximal strong salt for SHA-512 in bytes.
     */
    private static final int SALT_LENGTH_SHA512 = 64;

    /**
     * The random generator for hashing passwords. Lazily initialized because it could be expensive.
     */
    private static SecureRandom secureRandom;

    /**
     * Creates an instance from the {Base64(salt)}password formatted String.
     *
     * @param formattedPassword Password in the {Base64(salt)}password format
     */
    public ClearTextPassword(final String formattedPassword) {
        parse(formattedPassword);
    }

    /**
     * Creates a salted hash for a password using a SHA based hash function.
     * <p>
     * This method supports the following algorithms:
     * <ul>
     * <li>sha-256</li>
     * <li>sha-512</li>
     * </ul>
     *
     * @param hashFunction The hash function to use.
     * @param salt Salt in the form of byte array (may be {@code null}).
     * @param password The clear text password.
     * @return The salted hash as defined by
     * <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">Hono's Credentials API</a>.
     */
    public static String encode(final String hashFunction, final byte[] salt, final String password) {

        Objects.requireNonNull(hashFunction);
        Objects.requireNonNull(password);

        final PasswordEncoder encoder = new MessageDigestPasswordEncoder(hashFunction);
        final StringBuilder passwordToEncode = new StringBuilder();

        // Prepare password in the "{salt}password" format as that's expected by MessageDigestPasswordEncoder class
        append(salt, passwordToEncode);
        passwordToEncode.append(password);
        return encoder.encode(passwordToEncode.toString());
    }

    /**
     * Creates a salted hash for a password using the BCrypt hash function.
     * <p>
     * Invokes {@link #encodeBCrypt(String, int)} using 10 as strength.
     * 
     * @param password The clear text password.
     * @return The hash value as defined by
     * <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">Hono's Credentials API</a>.
     * @throws NullPointerException if password is {@code null}.
     */
    public static String encodeBCrypt(final String password) {
        return encodeBCrypt(password, 10);
    }

    /**
     * Creates a salted hash for a password using the BCrypt hash function.
     * 
     * @param password The clear text password.
     * @param strength The number of iterations to use when creating the hash.
     * @return The hash value as defined by
     * <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">Hono's Credentials API</a>.
     * @throws NullPointerException if password is {@code null}.
     * @throws IllegalArgumentException if the given strength is &lt; 4 or &gt; 31.
     */
    public static String encodeBCrypt(final String password, final int strength) {

        Objects.requireNonNull(password);

        final PasswordEncoder encoder = new BCryptPasswordEncoder(strength);
        return encoder.encode(password);
    }

    /**
     * Generates a random salt in the maximal length for the given hash function. This method supports the following
     * algorithms:
     * <ul>
     * <li>sha-256</li>
     * <li>sha-512</li>
     * </ul>
     * 
     * For "bcrypt" {@code null} is returned
     *
     * @param hashFunction The hash function to use.
     * @return The random salt or {@code null} if hashFunction is "bcrypt".
     * @throws IllegalArgumentException if the given is unknown.
     */
    public static byte[] randomSaltFor(final String hashFunction) {
        final byte[] salt;

        switch (hashFunction) {
        case CredentialsConstants.HASH_FUNCTION_BCRYPT:
            salt = null; // encodeBCrypt() already computes a salt
            break;
        case CredentialsConstants.HASH_FUNCTION_SHA256:
            salt = randomSalt(SALT_LENGTH_SHA256);
            break;
        case CredentialsConstants.HASH_FUNCTION_SHA512:
            salt = randomSalt(SALT_LENGTH_SHA512);
            break;
        default:
            throw new IllegalArgumentException("Unknown hash function [" + hashFunction + "].");
        }
        return salt;
    }

    private static byte[] randomSalt(final int length) {
        final byte[] salt = new byte[length];
        getSecureRandom().nextBytes(salt);
        return salt;
    }

    private static SecureRandom getSecureRandom() {
        // this rather complicated setup is intended to prevent blocking, which is especially an issue in containers
        // see: https://hackernoon.com/hack-how-to-use-securerandom-with-kubernetes-and-docker-a375945a7b21
        // and: https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/

        if (secureRandom == null) {
            try {
                secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking"); // non-blocking UNIX
            } catch (NoSuchAlgorithmException e) {
                secureRandom = new SecureRandom(); // might block
            }
        }
        return secureRandom;
    }
}
