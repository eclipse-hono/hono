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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A credential type for storing a Pre-shared Key as used in TLS handshakes.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#pre-shared-key">Pre-Shared Key</a> for an example
 * of the configuration properties for this credential type.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PskCredential extends CommonCredential {

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_PRESHARED_KEY;

    private final List<PskSecret> secrets = new LinkedList<>();

    /**
     * Creates a new credentials object for an authentication identifier.
     *
     * @param authId The authentication identifier.
     * @param secrets The credential's shared secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if auth ID does not match {@link org.eclipse.hono.util.CredentialsConstants#PATTERN_AUTH_ID_VALUE}
     *                                  or if secrets is empty.
     */
    public PskCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true) final List<PskSecret> secrets) {
        super(authId);
        setSecrets(secrets);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonIgnore
    public final String getType() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @return An unmodifiable list of secrets.
     */
    @Override
    @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS)
    public final List<PskSecret> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    /**
     * Sets the list of PSK secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to its validity period.
     *
     * @param secrets The secret to set.
     * @return        a reference to this for fluent use.
     * @throws NullPointerException if secrets is {@code null}.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public final PskCredential setSecrets(final List<PskSecret> secrets) {
        Objects.requireNonNull(secrets);
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets cannot be empty");
        }
        this.secrets.clear();
        this.secrets.addAll(secrets);
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the shared key from all secrets.
     */
    @Override
    public final PskCredential stripPrivateInfo() {
        getSecrets().forEach(PskSecret::stripPrivateInfo);
        return this;
    }
}
