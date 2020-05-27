/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.management.credentials;

import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.auth.BCryptHelper;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.vertx.core.json.JsonObject;

/**
 * This class encapsulates the secrets information for a password credentials type.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PasswordSecret extends CommonSecret {

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION)
    private String hashFunction;
    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH)
    private String passwordHash;
    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN)
    private String passwordPlain;
    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_SALT)
    private String salt;

    public final String getHashFunction() {
        return hashFunction;
    }

    /**
     * Sets the cryptographic hash function to use for hashing the UTF-8 clear text
     * password and salt (if set).
     * <p>
     * Currently, Hono supports the <b>sha-256</b>, <b>sha-512</b> and <b>bcrypt</b> hash functions.
     *
     * @param hashFunction  The cryptographic hashing function to use.
     * @return              a reference to this for fluent use.
     */
    public final PasswordSecret setHashFunction(final String hashFunction) {
        this.hashFunction = hashFunction;
        return this;
    }

    public final String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the cryptographic password hash value for this secret. 
     * <p>
     * The password hash value is the result of applying one of Hono's supported hash functions
     * to the plain text password and salt (if set).
     *
     * @param passwordHash  The cryptographic hash to set for this password.
     * @return              a reference to this for fluent use.
     */
    public final PasswordSecret setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
        return this;
    }

    public final String getPasswordPlain() {
        return passwordPlain;
    }

    /**
     * Sets the UTF-8 encoding of the plain text password.
     *
     * @param passwordPlain  The UTF-8 encoded plain text password to set.
     * @return               a reference to this for fluent use.
     */
    public final PasswordSecret setPasswordPlain(final String passwordPlain) {
        this.passwordPlain = passwordPlain;
        return this;
    }

    public final String getSalt() {
        return salt;
    }

    /**
     * Sets the Base64 encoding of the salt to append to this password before hashing.
     *
     * @param salt The Base64 encoding of the salt to use in the password hash.
     * @return     a reference to this for fluent use.
     */
    public final PasswordSecret setSalt(final String salt) {
        this.salt = salt;
        return this;
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("hashFunction", this.hashFunction)
                .add("pwdHash", this.passwordHash)
                .add("salt", this.salt);
    }

    /**
     * Checks if this secret's properties represent a <em>valid</em> state.
     * <p>
     * This implementation verifies that
     * <ul>
     * <li>the <em>notBefore</em> instant is before the <em>notAfter</em> instant and</li>
     * <li>either the containsOnlySecretId method returns {@code true} or</li>
     * <li>the <em>passwordPlain</em> property is {@code null} and the <em>hashFunction</em> and
     * <em>passwordHash</em> properties are not {@code null}</li>
     * </ul>
     * Subclasses may override this method in order to perform
     * additional checks.
     *
     * @throws IllegalStateException if the secret is not valid.
     */
    @Override
    public void checkValidity() {
        super.checkValidity();
        if (containsOnlySecretId()) {
            return;
        }
        if (!Strings.isNullOrEmpty(passwordPlain)) {
            throw new IllegalStateException(String.format("'%s' must be empty", RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN));
        }
        if (Strings.isNullOrEmpty(hashFunction)) {
            throw new IllegalStateException(String.format("'%s' must not be empty", RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION));
        }
        if (Strings.isNullOrEmpty(passwordHash)) {
            throw new IllegalStateException(String.format("'%s' must not be empty", RegistryManagementConstants.FIELD_SECRETS_PWD_HASH));
        }
    }

    /**
     * Checks if this secret contains an identifier only.
     *
     * @return {@code true} if the <em>id</em> property is not {@code null} and the
     *         <em>passwordPlain</em>, <em>hashFunction</em> and <em>passwordHash</em>
     *         properties are {@code null}.
     */
    public boolean containsOnlySecretId() {
        return (!Strings.isNullOrEmpty(getId())
                && Strings.isNullOrEmpty(passwordPlain)
                && Strings.isNullOrEmpty(hashFunction)
                && Strings.isNullOrEmpty(passwordHash));
    }

    /**
     * Encodes the clear text password contained in the <em>passwordPlain</em> field.
     * <p>
     * The hashFunction, passwordHash and salt fields are set to the values produced
     * by the given encoder. The passwordPlain field is set to {@code null}.
     * <p>
     * This method does nothing if the <em>passwordPlain</em> field is {@code null} or empty.
     *
     * @param encoder The password encoder to use.
     * @return A reference to this for fluent use.
     */
    public PasswordSecret encode(final HonoPasswordEncoder encoder) {
        if (!Strings.isNullOrEmpty(passwordPlain)) {
            final JsonObject hashedPassword = encoder.encode(passwordPlain);
            hashFunction = hashedPassword.getString(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION);
            passwordHash = hashedPassword.getString(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH);
            salt = hashedPassword.getString(RegistryManagementConstants.FIELD_SECRETS_SALT);
            passwordPlain = null;
        }
        return this;
    }

    /**
     * Checks if this secret uses a supported hash algorithm.
     * <p>
     * The check is successful if this secret
     * <ol>
     * <li>does not contain a hashed nor plain text password or</li>
     * <li>if this secret contains a hashed password, the
     * <ul>
     * <li>hash algorithm is contained in the given white list and</li>
     * <li>if the hash algorithm used is bcrypt, the {@link BCryptHelper#getIterations(String)}
     * method returns a value that is &le; the max iterations.</li>
     * </ul>
     * </li>
     * </ol>
     *
     * @param hashAlgorithmsWhitelist The list of supported hashing algorithms for pre-hashed passwords.
     * @param maxBcryptIterations The maximum number of iterations to use for bcrypt password hashes.
     * @throws IllegalStateException if this secret doesn't use a supported and valid hash algorithm.
     * @throws NullPointerException if the white list is {@code null}.
     */
    public final void verifyHashAlgorithm(final Set<String> hashAlgorithmsWhitelist, final int maxBcryptIterations) {

        Objects.requireNonNull(hashAlgorithmsWhitelist);

        if (containsOnlySecretId()) {
            return;
        } else if (hashFunction != null) {

            if (!hashAlgorithmsWhitelist.isEmpty() && !hashAlgorithmsWhitelist.contains(hashFunction)) {
                throw new IllegalStateException(String.format("unsupported hashing algorithm [%s]", hashFunction));
            }
            switch (hashFunction) {
            case RegistryManagementConstants.HASH_FUNCTION_BCRYPT:
                try {
                    if (BCryptHelper.getIterations(passwordHash) > maxBcryptIterations) {
                        throw new IllegalStateException("BCrypt hash algorithm uses too many iterations, max is " + maxBcryptIterations);
                    }
                    break;
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("password hash is not a supported BCrypt hash", e);
                }
            default:
                // no additional checks for other hash algorithms
                break;
            }
        }
        return;
    }

    void stripPrivateInfo() {
        hashFunction = null;
        passwordPlain = null;
        passwordHash = null;
        salt = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void merge(final CommonSecret secret) {
        Objects.requireNonNull(secret);

        if (secret instanceof PasswordSecret && containsOnlySecretId()) {
            final PasswordSecret passwordSecret = (PasswordSecret) secret;
            passwordPlain = passwordSecret.getPasswordPlain();
            passwordHash = passwordSecret.getPasswordHash();
            hashFunction = passwordSecret.getHashFunction();
            salt = passwordSecret.getSalt();
        }
    }
}
