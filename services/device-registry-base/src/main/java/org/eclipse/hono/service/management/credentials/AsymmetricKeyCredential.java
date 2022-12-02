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
 * A credential type for storing an asymmetric key (public key / certificate) for a device.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">Asymmetric Key</a> for an example of
 * the configuration properties for this credential type. TODO: provide correct URL
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class AsymmetricKeyCredential extends CommonCredential {

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_ASYMMETRIC_KEY;

    private final List<AsymmetricKeySecret> secrets = new ArrayList<>();

    /**
     * Creates a new credentials object for an authentication identifier.
     *
     * @param authId The authentication identifier.
     * @param secrets The credential's shared secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if secrets is empty.
     */
    public AsymmetricKeyCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true) final List<AsymmetricKeySecret> secrets) {
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
    public final List<AsymmetricKeySecret> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    /**
     * Sets the list of asymmetric key secrets to use for authenticating a device to protocol adapters.
     *
     * @param secrets The list of secrets to set.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if secrets is {@code null}.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public final AsymmetricKeyCredential setSecrets(final List<AsymmetricKeySecret> secrets) {
        Objects.requireNonNull(secrets);
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets must not be empty");
        }
        this.secrets.clear();
        this.secrets.addAll(secrets);
        return this;
    }
}
