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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A credential type for storing a password for a device.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">Hashed Password</a> for an example
 * of the configuration properties for this credential type.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PasswordCredential extends CommonCredential {

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD;

    @JsonProperty
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private List<PasswordSecret> secrets = new LinkedList<>();

    /**
     * Creates a new credentials object for an authentication identifier.
     *
     * @param authId The authentication identifier.
     * @throws NullPointerException if the auth ID is {@code null}.
     * @throws IllegalArgumentException if auth ID does not match {@link org.eclipse.hono.util.CredentialsConstants#PATTERN_AUTH_ID_VALUE}.
     */
    public PasswordCredential(@JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId) {
        super(authId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonIgnore
    public final String getType() {
        return TYPE;
    }

    @Override
    public List<PasswordSecret> getSecrets() {
        return secrets;
    }

    /**
     * Sets the list of password secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to its validity period.
     *
     * @param secrets The list of password secrets to set.
     * @return        a reference to this for fluent use.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public PasswordCredential setSecrets(final List<PasswordSecret> secrets) {
        if (secrets != null && secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets cannot be empty");
        }
        this.secrets = secrets;
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes hash function, password hash, salt and plaintext password from all secrets.
     */
    @Override
    public final PasswordCredential stripPrivateInfo() {
        getSecrets().forEach(PasswordSecret::stripPrivateInfo);
        return this;
    }
}
