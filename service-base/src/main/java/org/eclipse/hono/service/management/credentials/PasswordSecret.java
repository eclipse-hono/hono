/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;
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

    public String getHashFunction() {
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
    public PasswordSecret setHashFunction(final String hashFunction) {
        this.hashFunction = hashFunction;
        return this;
    }

    public String getPasswordHash() {
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
    public PasswordSecret setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
        return this;
    }

    public String getPasswordPlain() {
        return passwordPlain;
    }

    /**
     * Sets the UTF-8 encoding of the plain text password.
     *
     * @param passwordPlain  The UTF-8 encoded plain text password to set.
     * @return               a reference to this for fluent use.
     */
    public PasswordSecret setPasswordPlain(final String passwordPlain) {
        this.passwordPlain = passwordPlain;
        return this;
    }

    public String getSalt() {
        return salt;
    }

    /**
     * Sets the Base64 encoding of the salt to append to this password before hashing.
     *
     * @param salt The Base64 encoding of the salt to use in the password hash.
     * @return     a reference to this for fluent use.
     */
    public PasswordSecret setSalt(final String salt) {
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

    @Override
    public void checkValidity() {
        super.checkValidity();
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
     * Verifies that only the "password-plain" field is set in the Password Secret, i.e. the password is not hashed.
     *
     * @throws IllegalStateException if this is not the case.
     */
    public void checkIsPlainText() {
        if (Strings.isNullOrEmpty(passwordPlain)) {
            throw new IllegalStateException(String.format("'%s' must be provided", RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN));
        }
        if (!Strings.isNullOrEmpty(hashFunction)) {
            throw new IllegalStateException(String.format("'%s' must be empty", RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION));
        }
        if (!Strings.isNullOrEmpty(passwordHash)) {
            throw new IllegalStateException(String.format("'%s' must be empty", RegistryManagementConstants.FIELD_SECRETS_PWD_HASH));
        }
        if (!Strings.isNullOrEmpty(salt)) {
            throw new IllegalStateException(String.format("'%s' must be empty", RegistryManagementConstants.FIELD_SECRETS_SALT));
        }
    }

    /**
     * Encodes the clear text password contained in the <em>pwd-plain</em> field.
     * 
     * @param encoder The password encoder to use.
     * @return        a reference to this for fluent use.
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

}
