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

import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_PWD_HASH;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN;
import static org.eclipse.hono.util.RegistryManagementConstants.FIELD_SECRETS_SALT;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects.ToStringHelper;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.util.Strings;
import io.vertx.core.json.JsonObject;

/**
 * Secret Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PasswordSecret extends CommonSecret {

    @JsonProperty(FIELD_SECRETS_HASH_FUNCTION)
    private String hashFunction;
    @JsonProperty(FIELD_SECRETS_PWD_HASH)
    private String passwordHash;
    @JsonProperty(FIELD_SECRETS_PWD_PLAIN)
    private String passwordPlain;
    @JsonProperty(FIELD_SECRETS_SALT)
    private String salt;

    public String getHashFunction() {
        return hashFunction;
    }

    public void setHashFunction(final String hashFunction) {
        this.hashFunction = hashFunction;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordPlain() {
        return passwordPlain;
    }

    public void setPasswordPlain(final String passwordPlain) {
        this.passwordPlain = passwordPlain;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(final String salt) {
        this.salt = salt;
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
            throw new IllegalStateException(String.format("'%s' must be empty", FIELD_SECRETS_PWD_PLAIN));
        }
        if (Strings.isNullOrEmpty(hashFunction)) {
            throw new IllegalStateException(String.format("'%s' must not be empty", FIELD_SECRETS_HASH_FUNCTION));
        }
        if (Strings.isNullOrEmpty(passwordHash)) {
            throw new IllegalStateException(String.format("'%s' must not be empty", FIELD_SECRETS_PWD_HASH));
        }
    }

    /**
     * Encodes the value in {@link #passwordPlain} using provided encoder.
     * @param encoder Password encoder to use.
     */
    public void encode(final HonoPasswordEncoder encoder) {
        if (!Strings.isNullOrEmpty(passwordPlain)) {
            final JsonObject hashedPassword = encoder.encode(passwordPlain);
            hashFunction = hashedPassword.getString(FIELD_SECRETS_HASH_FUNCTION);
            passwordHash = hashedPassword.getString(FIELD_SECRETS_PWD_HASH);
            salt = hashedPassword.getString(FIELD_SECRETS_SALT);
            passwordPlain = null;
        }
    }

}
