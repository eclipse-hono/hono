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

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * A Hono specific {@code PasswordEncoder} that uses {@link MessageDigest} to encode passwords.
 * <p>
 * This class creates and matches password hashes based on the SHA-256 and SHA-512 algorithms as
 * defined by <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">
 * Hono's Credentials API</a>.
 */
public class MessageDigestPasswordEncoder implements PasswordEncoder {

    private final MessageDigest messageDigest;

    /**
     * Creates message digest password encoder with specified hash function.
     *
     * @param hashFunction - hash function to be used
     * @throws IllegalArgumentException if hash function is not valid
     */
    public MessageDigestPasswordEncoder(final String hashFunction) {
        try {
            messageDigest = MessageDigest.getInstance(hashFunction);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such hash function: " + e);
        }
    }

    /**
     *  Creates a hash for a clear text password.
     *
     *  @param rawPassword The password to hash. The password may also (optionally) be prefixed
     *                     by <em>salt</em> that should be used for computing the hash value.
     *                     If given, the salt needs to be provided in the form of the Base64 encoding
     *                     of the salt bytes wrapped in curly braces:
     *                     <em>{Base64(salt)}password</em>.
     *  @return The Base64 encoding of the bytes resulting from applying the hash function to
     *          the byte array consisting of the salt bytes (if a salt is used) and the UTF-8
     *          encoding of the clear text password.
     */
    @Override
    public String encode(final CharSequence rawPassword) {

        // parse given string into (optional) salt and password
        final ClearTextPassword password = new ClearTextPassword(rawPassword.toString());

        return Base64.getEncoder().encodeToString(digest(password.salt, password.password));
    }

    /**
     * Verifies that password provided matches the one we have in the store of record. The stored password is in the {Base64(salt)}passwordHash format.
     * The method will extract the salt (if present) and use it to encode the raw provided password. Then it will match the result with the
     * hash on the record.
     *
     * @param rawPassword Password to verify in plain text
     * @param encodedPassword Encoded password on the record in {Base64(salt)}passwordHash format
     * @return {@code true} if encoded password hash matches the one on the record, {@code false} otherwise
     */
    @Override
    public boolean matches(final CharSequence rawPassword, final String encodedPassword) {
        final EncodedPassword password = new EncodedPassword(encodedPassword);
        final byte[] digested = digest(password.salt, rawPassword.toString());

        return Arrays.equals(digested, Base64.getDecoder().decode(password.password));
    }

    /**
     * Creates a salted hash for a password.
     * The hash is computed by applying the hash function to the byte array consisting of the salt bytes (if a salt is used) and the UTF-8 encoding of the clear text password.
     *
     * @param salt Salt to be used. Can be {@code null}
     * @param password Clear text password
     * @return Password hash
     */
    protected byte[] digest(final byte[] salt, final String password) {
        if (salt != null) {
            messageDigest.update(salt);
        }
        return messageDigest.digest(password.getBytes(StandardCharsets.UTF_8));
    }

}
