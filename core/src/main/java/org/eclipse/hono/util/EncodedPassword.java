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
public class EncodedPassword extends BasePassword {

    /**
     * Creates an empty instance.
     */
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
     * <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">Hono's Credentials API</a>.
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
}
