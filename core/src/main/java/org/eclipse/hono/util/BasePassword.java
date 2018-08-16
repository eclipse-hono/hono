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

import java.util.Base64;
import java.util.Objects;

/**
 * Abstract base class for parsing and encoding passwords.
 * <p>
 * The password have the following main elements:
 * <ul>
 *     <li><em>Hash function</em> that are used to encode the password</li>
 *     <li><em>Salt</em> which is optionally used for encoding</li>
 *     <li><em>Password</em> we want to encode in clear text form or hashed value which is used for matching</li>
 * </ul>
 *
 */
abstract public class BasePassword {
    private static final String PREFIX = "{";
    private static final String SUFFIX = "}";

    public byte[] salt;
    public String password;
    public String hashFunction =  CredentialsConstants.DEFAULT_HASH_FUNCTION;

    /**
     * Initializes the object from the String in {Base64(salt)}password format. The password can be in clear text or hashed format.
     *
     *
     * @param formattedPassword Password in the {Base64(salt)}password format
     */
    protected void parse(final String formattedPassword) {
        Objects.requireNonNull(formattedPassword);

        final int start = formattedPassword.indexOf(PREFIX);
        this.password = formattedPassword;

        if (start == 0) {
            final int end = formattedPassword.indexOf(SUFFIX, start);
            if (end > 0) {
                this.salt = Base64.getDecoder().decode(formattedPassword.substring(start + 1, end));
                this.password = formattedPassword.substring(end + 1);
            }
        }
    }

    /**
     * Formats the password to {hashFunction}{Base64(salt)}{passwordHash} format which is used by Spring Security password encoders.
     *
     * @return Value of this object formatted as {hashFunction}{Base64(salt)}{passwordHash} String
     */
    public String format() {
        final StringBuilder result = new StringBuilder(PREFIX).append(hashFunction).append(SUFFIX);
        append(salt, result);
        result.append(password);

        return result.toString();
    }

    /**
     * Helper function that appends Base64 encoded value (if not {@code null}) to the provided String builder, using "{value}" format.
     *
     * @param value Value to append to the builder
     * @param builder String builder to append value to
     */
    protected static void append(final byte[] value, final StringBuilder builder) {
        if (value != null) {
            builder
                    .append(PREFIX)
                    .append(Base64.getEncoder().encodeToString(value))
                    .append(SUFFIX);
        }
    }

    @Override
    public String toString() {
        return format();
    }

}
