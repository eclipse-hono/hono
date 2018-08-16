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

import io.vertx.core.json.JsonObject;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Base64;
import java.util.Objects;

/**
 * Encoded password representation. It's used to parse Strings in the form of {Base64(salt)}password-hash into the value object, like
 *
 * <pre>
 * EncodedPassword password = new EncodedPassword("{VGhlU2FsdA==}1L/qmnQ8kbgckAodOCbtyJAhoiK4k0rBtBBN+WD+TIE=");
 * System.out.println(password.salt + " " + password.password);
 * </pre>
 *
 * You can also use it to match passwords, like
 *
 * <pre>
 * password.matches("ThePassword");
 * </pre>
 *
 * This class also support matching passwords encrypted using BCrypt hashing function
 *
 * <pre>
 * EncodedPassword password = new EncodedPassword("$2a$12$BjLeC/gqcnEyk.XNo2qorul.a/v4HDuOUlfmojdSZXRSFTjymPdVm");
 * password.hashFunction = "bcrypt";
 * password.matches("kapua-password");
 * </pre>
 *
 */
public class EncodedPassword extends BasePassword {

    /**
     * Creates an instance from the {Base64(salt)}password-hash formatted String.
     *
     * @param formattedPassword Password hash in the {Base64(salt)}password-hash format
     */
    public EncodedPassword(final String formattedPassword) {
        parse(formattedPassword);
    }

    /**
     * Creates an empty instance.
     */
    public EncodedPassword() {
    }

    /**
     * Creates a new instance from Hono Secret.
     *
     * The secret is expected to be of type <em>hashed-password</em> as defined by
     * <a href="https://www.eclipse.org/hono/api/credentials-api/#hashed-password">Hono's Credentials API</a>.
     *
     * @param honoSecret JSON object that contains hono-formatted secret
     * @return Encoded password
     * @throws IllegalArgumentException if candidate hashed-password secret does not contain a password hash
     */
    public static EncodedPassword fromHonoSecret(final JsonObject honoSecret) throws IllegalArgumentException {
        Objects.requireNonNull(honoSecret);

        final String pwdHash = honoSecret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        if (pwdHash == null) {
            throw new IllegalArgumentException("candidate hashed-password secret does not contain a pwd hash");
        }

        final String hashFunction = honoSecret.getString(
                CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION,
                CredentialsConstants.DEFAULT_HASH_FUNCTION);

        final String encodedSalt = honoSecret.getString(CredentialsConstants.FIELD_SECRETS_SALT);
        final EncodedPassword encodedPassword = new EncodedPassword();
        encodedPassword.hashFunction = hashFunction;
        encodedPassword.password = pwdHash;
        if (encodedSalt != null) {
            encodedPassword.salt = Base64.getDecoder().decode(encodedSalt);
        }
        return encodedPassword;
    }

    /**
     * Checks if provided password (in clear text form) matches this encoded password.
     *
     * @param password Password to match
     * @return {@code true} if the password matches the stored secret.
     */
    public boolean matches(final String password) {
        Objects.requireNonNull(password);
        final PasswordEncoder encoder = PasswordEncoderFactory.createDelegatingPasswordEncoder();
        return encoder.matches(password, format());
    }

}
