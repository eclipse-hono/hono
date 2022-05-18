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
package org.eclipse.hono.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A Hono specific {@code PasswordEncoder} that uses a {@link MessageDigest} to encode passwords.
 * <p>
 * Passwords will be hashed using a random salt of 8 bytes.
 */
public class MessageDigestPasswordEncoder implements PasswordEncoder {

    private static final Character PREFIX = '{';
    private static final Character SUFFIX = '}';

    private final SecureRandom rng;
    private final String hashFunction;

    /**
     * Creates message digest password encoder with specified hash function.
     * <p>
     * This constructor invokes {@link #MessageDigestPasswordEncoder(String, SecureRandom)}
     * with a newly created {@code SecureRandom}.
     *
     * @param hashFunction The hash function to use.
     * @throws IllegalArgumentException if the JVM does not support the hash function.
     */
    public MessageDigestPasswordEncoder(final String hashFunction) {
        this(hashFunction, new SecureRandom());
    }

    /**
     * Creates message digest password encoder with specified hash function.
     *
     * @param hashFunction - hash function to be used
     * @param rng The random number generator to use for creating salt.
     * @throws IllegalArgumentException if hash function is not valid
     * @throws NullPointerException if any of the parameters are {@code null}
     */
    public MessageDigestPasswordEncoder(final String hashFunction, final SecureRandom rng) {

        Objects.requireNonNull(hashFunction);
        Objects.requireNonNull(rng);

        try {
            MessageDigest.getInstance(hashFunction);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("hash function [" + hashFunction + "] not supported on JVM", e);
        }

        this.hashFunction = hashFunction;
        this.rng = rng;
    }

    /**
     *  Creates a hash for a clear text password.
     *
     *  @param rawPassword The password to hash. A randomly generated salt will be used
     *          for hashing the password.
     *  @return The encoded password hash. The value will be of the form
     *          <pre>
     *          "{" Base64(salt) "}" passwordHash
     *          </pre>
     *          Where passwordHash is the Base64 encoding of the bytes resulting from applying
     *          the hash function to the byte array consisting of the salt bytes
     *          and the UTF-8 encoding of the clear text password.
     */
    @Override
    public String encode(final CharSequence rawPassword) {

        final byte[] salt = randomSalt();
        return new StringBuilder()
                .append(PREFIX).append(Base64.getEncoder().encodeToString(salt)).append(SUFFIX)
                .append(Base64.getEncoder().encodeToString(digest(salt, rawPassword.toString())))
                .toString();
    }

    /**
     * Verifies that a clear text password matches a given encoded password hash.
     * <p>
     * The password hash is expected to be of the form
     * <pre>
     * "{" Base64(salt) "}" passwordHash
     * </pre>
     * Where passwordHash is the Base64 encoding of the bytes resulting from applying
     * the hash function to the byte array consisting of the salt bytes and the UTF-8
     * encoding of the clear text password.
     *
     * @param rawPassword Password to verify in plain text
     * @param encodedPassword Encoded password on the record in {Base64(salt)}passwordHash format
     * @return {@code true} if encoded password hash matches the one on record, {@code false} otherwise
     * @throws IllegalArgumentException if the encodedPassword does not contain valid Base64 schema.
     */
    @Override
    public boolean matches(final CharSequence rawPassword, final String encodedPassword) {
        final EncodedPassword password = new EncodedPassword(encodedPassword);
        final byte[] digested = digest(password.salt, rawPassword.toString());
        final byte[] pwdHash = Base64.getDecoder().decode(password.password);
        return Arrays.equals(digested, pwdHash);
    }

    /**
     * Creates a salted hash for a password.
     * The hash is computed by applying the hash function to the byte array consisting
     * of the salt bytes (if a salt is used) and the UTF-8 encoding of the clear text password.
     *
     * @param salt Salt to be used. Can be {@code null}
     * @param password Clear text password
     * @return The salted hash.
     */
    private byte[] digest(final byte[] salt, final String password) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
            if (salt != null) {
                messageDigest.update(salt);
            }
            return messageDigest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("hash function [" + hashFunction + "] not supported on JVM", e);
        }
    }

    private byte[] randomSalt() {

        final byte[] salt = new byte[8];
        rng.nextBytes(salt);
        return salt;
    }
}
