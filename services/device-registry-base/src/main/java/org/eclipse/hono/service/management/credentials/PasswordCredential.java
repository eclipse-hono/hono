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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.util.CredentialsConstants;
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

    /**
     * The regular expression that authentication identifiers need to match.
     */
    public static final String REGEX_AUTH_ID = "^[a-zA-Z0-9-_=\\.]+$";

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD;

    private static final Pattern PATTERN_AUTH_ID_VALUE = Pattern.compile(REGEX_AUTH_ID);
    /**
     * A predicate for matching authentication identifiers against the
     * {@linkplain CredentialsConstants#PATTERN_AUTH_ID_VALUE default pattern}.
     */
    private static final Predicate<String> AUTH_ID_VALIDATOR_DEFAULT = authId -> {
        final Matcher matcher = PATTERN_AUTH_ID_VALUE.matcher(authId);
        if (matcher.matches()) {
            return true;
        } else {
            throw new IllegalArgumentException("authentication identifier must match pattern "
                    + PATTERN_AUTH_ID_VALUE.pattern());
        }
    };

    private final List<PasswordSecret> secrets = new LinkedList<>();

    /**
     * Creates a new credentials object for an authentication identifier.
     *
     * @param authId The authentication identifier.
     * @param secrets The credential's secret password(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if auth ID does not match {@value #REGEX_AUTH_ID} or if secrets is empty.
     */
    public PasswordCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String authId,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true) final List<PasswordSecret> secrets) {

        super(authId);
        setSecrets(secrets);
    }

    /**
     * {@inheritDoc}
     *
     * @return A predicate that matches the identifier against the {@linkplain #REGEX_AUTH_ID default pattern}.
     */
    @Override
    protected Predicate<String> getAuthIdValidator() {
        return AUTH_ID_VALIDATOR_DEFAULT;
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
    public final List<PasswordSecret> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    /**
     * Sets the list of password secrets to use for authenticating a device to protocol adapters.
     *
     * @param secrets The list of password secrets to set.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if secrets is {@code null}.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public final PasswordCredential setSecrets(final List<PasswordSecret> secrets) {
        Objects.requireNonNull(secrets);
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets must not be empty");
        }
        this.secrets.clear();
        this.secrets.addAll(secrets);
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes hash function, password hash, salt and plain text password from all secrets.
     */
    @Override
    public final PasswordCredential stripPrivateInfo() {
        getSecrets().forEach(PasswordSecret::stripPrivateInfo);
        return this;
    }
}
