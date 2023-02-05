/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.service.management.credentials;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A credential type for storing a raw public key for a device.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#raw-public-key">Raw Public Key</a> for an example
 * of the configuration properties for this credential type.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class RpkCredential extends CommonCredential {

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_RAW_PUBLIC_KEY;

    private final List<RpkSecret> secrets = new ArrayList<>();

    /**
     * Creates a new credentials object for an authentication identifier.
     *
     * @param authId The authentication identifier.
     * @param secrets The credential's shared secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if secrets is empty.
     */
    public RpkCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true) final List<RpkSecret> secrets) {
        super(authId);
        setSecrets(secrets);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonIgnore
    public String getType() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @return An unmodifiable list of secrets.
     */
    @Override
    @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS)
    public List<RpkSecret> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    /**
     * Sets the list of raw public key secrets to use for authenticating a device to protocol adapters.
     *
     * @param secrets The list of secrets to set.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if secrets is {@code null}.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public RpkCredential setSecrets(final List<RpkSecret> secrets) {
        Objects.requireNonNull(secrets);
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets must not be empty");
        }
        this.secrets.clear();
        this.secrets.addAll(secrets);
        return this;
    }
}
