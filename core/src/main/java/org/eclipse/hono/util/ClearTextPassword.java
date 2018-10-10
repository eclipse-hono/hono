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
}
