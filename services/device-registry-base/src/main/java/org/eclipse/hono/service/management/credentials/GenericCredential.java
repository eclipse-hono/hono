/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A generic credentials implementation.
 */
@RegisterForReflection
public class GenericCredential extends CommonCredential {

    private static final Predicate<String> typeValidator = CredentialsConstants.PATTERN_TYPE_VALUE.asMatchPredicate();

    private final String type;
    private final List<GenericSecret> secrets = new ArrayList<>();

    private Map<String, Object> additionalProperties = new HashMap<>();


    /**
     * Creates a new credentials object for a type and authentication identifier.
     *
     * @param type The credentials type to set.
     * @param authId The authentication identifier.
     * @param secrets The credential's (generic) secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if type does not match {@link CredentialsConstants#PATTERN_TYPE_VALUE} or
     *                                  if secrets is empty.
     */
    public GenericCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_TYPE, required = true) final String type,
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true) final List<GenericSecret> secrets) {
        super(authId);
        Objects.requireNonNull(type);
        if (typeValidator.test(type)) {
            this.type = type;
        } else {
            throw new IllegalArgumentException("type name must match pattern "
                    + CredentialsConstants.PATTERN_TYPE_VALUE.pattern());
        }
        setSecrets(secrets);
    }

    @Override
    @JsonIgnore
    public final String getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     *
     * @return An unmodifiable list of secrets.
     */
    @Override
    @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS)
    public final List<GenericSecret> getSecrets() {
        return Collections.unmodifiableList(this.secrets);
    }

    /**
     * Sets the list of secrets to use for authenticating a device to protocol adapters.
     *
     * @param secrets The secret to set.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if secrets is {@code null}.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public final GenericCredential setSecrets(final List<GenericSecret> secrets) {
        Objects.requireNonNull(secrets);
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("Secrets cannot be empty");
        }
        this.secrets.clear();
        this.secrets.addAll(secrets);
        return this;
    }

    /**
     * Sets the additional properties for this credential.
     *
     * @param additionalProperties  The additional properties for this credential.
     * @return                      a reference to this for fluent use.
     */
    @JsonAnySetter
    public final GenericCredential setAdditionalProperties(final Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    @JsonAnyGetter
    public final Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

}
