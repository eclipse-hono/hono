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
     * Creates a salted hash for a password.
     * <p>
     * Gets the password's UTF-8 bytes, prepends them with the salt (if not {@code null}
     * and returns the output of the hash function applied to the byte array.
     *
     * @param hashFunction Hash function to use
     * @param salt Salt in the form of byte array
     * @param password Password to hash
     * @return Salted hash for a password as a Base64 formatted String
     */
    public static String encode(final String hashFunction, final byte[] salt, final String password) {
        Objects.requireNonNull(hashFunction);
        Objects.requireNonNull(password);

        //TODO Delegating encoder support encoding only using default encoder. Maybe we can enhance the interface to support more hash functions
        final PasswordEncoder encoder = new MessageDigestPasswordEncoder(hashFunction);
        // Prepare password in the "{salt}password" format as that's expected by PasswordEncoder class
        final StringBuilder passwordToEncode = new StringBuilder();
        append(salt, passwordToEncode);
        passwordToEncode.append(password);
        return encoder.encode(passwordToEncode.toString());
    }

}
