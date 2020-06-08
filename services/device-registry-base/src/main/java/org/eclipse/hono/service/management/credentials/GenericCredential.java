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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A generic credentials implementation.
 */
public class GenericCredential extends CommonCredential {

    private static final Predicate<String> typeValidator = CredentialsConstants.PATTERN_TYPE_VALUE.asMatchPredicate();

    @JsonProperty(value = RegistryManagementConstants.FIELD_TYPE)
    private String type;

    @JsonAnySetter
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private List<GenericSecret> secrets = new LinkedList<>();

    /**
     * Creates a new credentials object for a type and authentication identifier.
     *
     * @param type The credentials type to set.
     * @param authId The authentication identifier.
     * @throws NullPointerException if type or auth ID are {@code null}.
     * @throws IllegalArgumentException if type does not match {@link CredentialsConstants#PATTERN_TYPE_VALUE}
     *         or if auth ID does not match {@link CredentialsConstants#PATTERN_AUTH_ID_VALUE}.
     */
    public GenericCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_TYPE, required = true) final String type,
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId) {
        super(authId);
        Objects.requireNonNull(type);
        if (typeValidator.test(type)) {
            this.type = type;
        } else {
            throw new IllegalArgumentException("type name must match pattern "
                    + CredentialsConstants.PATTERN_TYPE_VALUE.pattern());
        }
    }

    @Override
    public final String getType() {
        return type;
    }

    @Override
    public final List<GenericSecret> getSecrets() {
        return this.secrets;
    }

    /**
     * Sets the list of secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to its validity period.
     *
     * @param secrets The secret to set.
     * @return        a reference to this for fluent use.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public final GenericCredential setSecrets(final List<GenericSecret> secrets) {
        if (secrets != null && secrets.isEmpty()) {
            throw new IllegalArgumentException("Secrets cannot be empty");
        }
        this.secrets = secrets;
        return this;
    }

    /**
     * Sets the additional properties for this credential.
     *
     * @param additionalProperties  The additional properties for this credential.
     * @return                      a reference to this for fluent use.
     */
    public final GenericCredential setAdditionalProperties(final Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    @JsonAnyGetter
    public final Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

}
