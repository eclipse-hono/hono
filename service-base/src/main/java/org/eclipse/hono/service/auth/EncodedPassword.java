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

import java.util.Base64;
import java.util.Objects;

import org.eclipse.hono.util.CredentialsConstants;

import io.vertx.core.json.JsonObject;

/**
 * Encoded password representation.
 * <p>
 * Helps with parsing strings in the form of {Base64(salt)}password-hash into a value object, like
 *
 * <pre>
 * EncodedPassword password = new EncodedPassword("{VGhlU2FsdA==}1L/qmnQ8kbgckAodOCbtyJAhoiK4k0rBtBBN+WD+TIE=");
 * System.out.println(password.salt + " " + password.password);
 * </pre>
 */
public final class EncodedPassword {

    private static final String PREFIX = "{";
    private static final String SUFFIX = "}";

    /**
     * The salt used for hashing the password or {@code null}
     * if no salt is used.
     */
    public byte[] salt;
    /**
     * The password hash if this object has been created from an encoded password
     * or the clear text password to be encoded.
     */
    public String password;
    /**
     * The hash function used for creating the hash value.
     * Defaults to {@link CredentialsConstants#DEFAULT_HASH_FUNCTION}.
     */
    public String hashFunction =  CredentialsConstants.DEFAULT_HASH_FUNCTION;

    private EncodedPassword() {
    }

    /**
     * Creates an instance from the {Base64(salt)}password-hash formatted String.
     *
     * @param formattedPassword Password hash in the {Base64(salt)}password-hash format
     */
    public EncodedPassword(final String formattedPassword) {
        parse(formattedPassword);
    }

    /**
     * Creates a new instance from Hono Secret.
     * <p>
     * The secret is expected to be of type <em>hashed-password</em> as defined by
     * <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">Hono's Credentials API</a>.
     *
     * @param secret JSON object that contains the Hono-formatted secret.
     * @return The password value object.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret does not contain a password hash
     *               or if the salt is not valid Base64 schema.
     */
    public static EncodedPassword fromHonoSecret(final JsonObject secret) throws IllegalArgumentException {

        Objects.requireNonNull(secret);

        final String pwdHash = CredentialsConstants.getPasswordHash(secret);
        if (pwdHash == null) {
            throw new IllegalArgumentException("hashed-password secret does not contain a pwd hash");
        }

        final String hashFunction = CredentialsConstants.getHashFunction(secret);
        final String encodedSalt = CredentialsConstants.getPasswordSalt(secret);
        final EncodedPassword encodedPassword = new EncodedPassword();
        encodedPassword.hashFunction = hashFunction;
        encodedPassword.password = pwdHash;
        if (encodedSalt != null) {
            encodedPassword.salt = Base64.getDecoder().decode(encodedSalt);
        }
        return encodedPassword;
    }

    /**
     * Initializes the object from the String in {Base64(salt)}password format. The password can be in clear text or hashed format.
     *
     * @param formattedPassword Password in the {Base64(salt)}password format. The <em>{Base64(salt)}</em> is optional.
     */
    private void parse(final String formattedPassword) {
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
     * Creates a string representation of this password that is compatible with
     * Spring Security password encoders.
     *
     * @return The value of this object formatted as {Base64(salt)}password.
     */
    public String format() {
        final StringBuilder result = new StringBuilder();
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
    private static void append(final byte[] value, final StringBuilder builder) {
        if (value != null) {
            builder
                    .append(PREFIX)
                    .append(Base64.getEncoder().encodeToString(value))
                    .append(SUFFIX);
        }
    }
}
