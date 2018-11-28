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

package org.eclipse.hono.service.credentials;

import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.ClearTextPassword;
import org.eclipse.hono.util.CredentialsConstants;

import java.security.SecureRandom;

/**
 * Utility methods for hashing of plain text passwords for Credentials endpoint.
 */
public class CredentialsPlainPasswordHelper {

    /**
     * The cryptographically maximal strong salt for SHA-256 in bytes.
     */
    private static final int SALT_LENGTH_SHA256 = 32;
    /**
     * The cryptographically maximal strong salt for SHA-512 in bytes.
     */
    private static final int SALT_LENGTH_SHA512 = 64;

    private static final String DEFAULT_HASH_FUNCTION = CredentialsConstants.HASH_FUNCTION_BCRYPT;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CredentialsPlainPasswordHelper() {
        // prevent instantiation
    }

    /**
     * Hashes a plain text password in the field @link{CredentialsConstants.FIELD_SECRETS_PWD_PLAIN} and updates the
     * secret accordingly.
     *
     * @param secret The secret to be updated.
     * @return The updated secret.
     */
    public static JsonObject hashPwdAndUpdateSecret(final JsonObject secret) {

        if (secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, "").isEmpty()) {
            return secret;
        }

        final String hashFunction = secret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION,
                DEFAULT_HASH_FUNCTION);

        final String pwd = secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN);

        final byte[] salt;
        final String pwdHash;

        switch (hashFunction) {
        case CredentialsConstants.HASH_FUNCTION_BCRYPT:
            pwdHash = ClearTextPassword.encodeBCrypt(pwd);
            break;
        case CredentialsConstants.HASH_FUNCTION_SHA256:
            salt = randomSalt(SALT_LENGTH_SHA256);
            pwdHash = hashWithSha(hashFunction, pwd, salt);
            secret.put(CredentialsConstants.FIELD_SECRETS_SALT, salt);
            break;
        case CredentialsConstants.HASH_FUNCTION_SHA512:
            salt = randomSalt(SALT_LENGTH_SHA512);
            pwdHash = hashWithSha(hashFunction, pwd, salt);
            secret.put(CredentialsConstants.FIELD_SECRETS_SALT, salt);
            break;
        default:
            throw new IllegalArgumentException("Unknown hash function");
        }

        secret.put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, pwdHash);
        secret.remove(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN);

        return secret;
    }

    // Visible for testing
    static String hashWithSha(final String hashFunction, final String pwd, final byte[] salt) {
        return ClearTextPassword.encode(hashFunction, salt, pwd);
    }

    private static byte[] randomSalt(final int length) {
        final byte[] salt = new byte[length];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

}
