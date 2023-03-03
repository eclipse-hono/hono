/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Credentials API.
 */
public final class CredentialsConstants extends RequestResponseApiConstants {

    /**
     * The <em>aud</em> claim value indicating that a JWT is intended to be processed
     * by a Hono protocol adapter.
     */
    public static final String AUDIENCE_HONO_ADAPTER = "hono-adapter";

    /**
     * The name of the claim that contains the tenant that a device belongs to.
     */
    public static final String CLAIM_TENANT_ID = "tid";

    /**
     * The name of the field that contains the user name.
     */
    public static final String FIELD_USERNAME                    = "username";
    /**
     * The name of the field that contains the password.
     */
    public static final String FIELD_PASSWORD                    = "password";
    /**
     * The name of the field that contains the type of credentials.
     */
    public static final String FIELD_TYPE                        = "type";
    /**
     * The name of the field that contains the authentication identifier.
     */
    public static final String FIELD_AUTH_ID                     = "auth-id";
    /**
     * The name of the field that contains the secret(s) of the credentials.
     */
    public static final String FIELD_SECRETS                     = "secrets";
    /**
     * The name of the field that contains the number of credentials contained in a message.
     */
    public static final String FIELD_CREDENTIALS_TOTAL           = "total";

    /* secrets fields */
    /**
     * The name of the field that contains the password hash.
     */
    public static final String FIELD_SECRETS_ALGORITHM           = "algorithm";
    /**
     * The name of the field that contains the password hash.
     */
    public static final String FIELD_SECRETS_PWD_HASH            = "pwd-hash";
    /**
     * The name of the field that contains the clear text password.
     */
    public static final String FIELD_SECRETS_PWD_PLAIN           = "pwd-plain";
    /**
     * The name of the field that contains the salt for the password hash.
     */
    public static final String FIELD_SECRETS_SALT                = "salt";
    /**
     * The name of the field that contains the name of the hash function used for a hashed password.
     */
    public static final String FIELD_SECRETS_HASH_FUNCTION       = "hash-function";
    /**
     * The name of the field that contains a (pre-shared) key.
     */
    public static final String FIELD_SECRETS_KEY                 = "key";
    /**
     * The name of the field that contains the earliest point in time a secret may be used
     * for authentication.
     */
    public static final String FIELD_SECRETS_NOT_BEFORE          = "not-before";
    /**
     * The name of the field that contains the latest point in time a secret may be used
     * for authentication.
     */
    public static final String FIELD_SECRETS_NOT_AFTER           = "not-after";
    /**
     * The name of the field that contains the client certificate that is used for authentication.
     */
    public static final String FIELD_CLIENT_CERT                 = "client-certificate";
    /**
     * The Credential service's endpoint name.
     */
    public static final String CREDENTIALS_ENDPOINT              = "credentials";

    /**
     * The type name that indicates an X.509 client certificate secret.
     */
    public static final String SECRETS_TYPE_X509_CERT            = "x509-cert";
    /**
     * The type name that indicates a hashed password secret.
     */
    public static final String SECRETS_TYPE_HASHED_PASSWORD      = "hashed-password";
    /**
     * The type name that indicates a pre-shared key secret.
     */
    public static final String SECRETS_TYPE_PRESHARED_KEY        = "psk";
    /**
     * The type name that indicates a raw public key secret.
     */
    public static final String SECRETS_TYPE_RAW_PUBLIC_KEY = "rpk";
    /**
     * The name of the field that contains the pattern to use for matching authentication identifiers.
     */
    public static final String SPECIFIER_WILDCARD                = "*";

    /**
     * The name of the BCrypt hash function.
     */
    public static final String HASH_FUNCTION_BCRYPT              = "bcrypt";
    /**
     * The name of the SHA-256 hash function.
     */
    public static final String HASH_FUNCTION_SHA256              = "sha-256";
    /**
     * The name of the SHA-512 hash function.
     */
    public static final String HASH_FUNCTION_SHA512              = "sha-512";
    /**
     * The name of the default hash function to use for hashed passwords if not set explicitly.
     */
    public static final String DEFAULT_HASH_FUNCTION             = HASH_FUNCTION_SHA256;
    /**
     * The name of the supported rsa algorithm.
     */
    public static final String RSA_ALG                            = "RSA";
    /**
     * The name of the supported elliptic curve algorithm.
     */
    public static final String EC_ALG                            = "EC";
    /**
     * The vert.x event bus address to which inbound credentials messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_CREDENTIALS_IN = "credentials.in";
    /**
     * The regular expression to validate that the type field supplied in credentials is legal.
     */
    public static final Pattern PATTERN_TYPE_VALUE = Pattern.compile("^[a-z0-9-]+$");

    /**
     * Request actions that belong to the Credentials API.
     */
    public enum CredentialsAction {
        /**
         * The AMQP subject property value to use for invoking the <em>get Credentials</em> operation.
         */
        get,
        /**
         * The AMQP subject property value to use for invoking the <em>add Credentials</em> operation.
         */
        add,
        /**
         * The AMQP subject property value to use for invoking the <em>update Credentials</em> operation.
         */
        update,
        /**
         * The name that all unknown operations are mapped to.
         */
        unknown;

        /**
         * Construct a CredentialsAction from a subject.
         *
         * @param subject The subject from which the CredentialsAction needs to be constructed.
         * @return CredentialsAction The CredentialsAction as enum, or {@link CredentialsAction#unknown} otherwise.
         */
        public static CredentialsAction from(final String subject) {
            if (subject != null) {
                try {
                    return CredentialsAction.valueOf(subject);
                } catch (final IllegalArgumentException e) {
                    // fall through
                }
            }
            return unknown;
        }

        /**
         * Helper method to check if a subject is a valid Credentials API action.
         *
         * @param subject The subject to validate.
         * @return boolean {@link Boolean#TRUE} if the subject denotes a valid action, {@link Boolean#FALSE} otherwise.
         */
        public static boolean isValid(final String subject) {
            return CredentialsAction.from(subject) != CredentialsAction.unknown;
        }
    }

    private CredentialsConstants() {
        // prevent instantiation
    }

    /**
     * Creates a JSON object containing search criteria for Credentials.
     *
     * @param type The type of credentials to get.
     * @param authId The authentication ID to get credentials for.
     * @return The search criteria.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static JsonObject getSearchCriteria(final String type, final String authId) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);

        return new JsonObject()
                .put(FIELD_TYPE, type)
                .put(FIELD_AUTH_ID, authId);
    }

    /**
     * Gets the hash function of a hashed-password secret.
     *
     * @param secret The secret.
     * @return The hash function.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret contains a non-string valued
     *                                  hash function property.
     */
    public static String getHashFunction(final JsonObject secret) {

        Objects.requireNonNull(secret);
        return Optional.ofNullable(secret.getValue(FIELD_SECRETS_HASH_FUNCTION)).map(o -> {
            if (o instanceof String) {
                return (String) o;
            } else {
                throw new IllegalArgumentException("secret contains invalid hash function value");
            }
        }).orElse(DEFAULT_HASH_FUNCTION);
    }

    /**
     * Gets the password hash of a hashed-password secret.
     *
     * @param secret The secret.
     * @return The Base64 encoded password hash.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret does not contain a
     *                                  password hash property.
     */
    public static String getPasswordHash(final JsonObject secret) {

        Objects.requireNonNull(secret);
        return Optional.ofNullable(secret.getValue(FIELD_SECRETS_PWD_HASH)).map(o -> {
            if (o instanceof String) {
                return (String) o;
            } else {
                throw new IllegalArgumentException("secret contains invalid hash function value");
            }
        }).orElseThrow(() -> new IllegalArgumentException("secret does not contain password hash"));
    }

    /**
     * Gets the password salt of a hashed-password secret.
     *
     * @param secret The secret.
     * @return The Base64 encoded password salt or {@code null} if no salt is used.
     * @throws NullPointerException if secret is {@code null}.
     * @throws IllegalArgumentException if the secret contains a non-string
     *                                  valued password salt property.
     */
    public static String getPasswordSalt(final JsonObject secret) {

        Objects.requireNonNull(secret);
        return Optional.ofNullable(secret.getValue(FIELD_SECRETS_SALT)).map(o -> {
            if (o instanceof String) {
                return (String) o;
            } else {
                throw new IllegalArgumentException("secret contains invalid salt value");
            }
        }).orElse(null);
    }
}

