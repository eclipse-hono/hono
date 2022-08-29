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
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A credential type for storing a password for a device.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">Hashed Password</a> for an example
 * of the configuration properties for this credential type.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PasswordCredential extends CommonCredential {

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD;

    private static final Logger LOG = LoggerFactory.getLogger(PasswordCredential.class);
    /**
     * The pattern being used for validating authentication identifiers.
     */
    private static Pattern PATTERN_AUTH_ID_VALUE;

    private final List<PasswordSecret> secrets = new ArrayList<>();

    static {
        final String regex = System.getProperty(RegistryManagementConstants.SYSTEM_PROPERTY_USERNAME_REGEX);
        if (Strings.isNullOrEmpty(regex)) {
            setAuthIdPattern(RegistryManagementConstants.DEFAULT_PATTERN_USERNAME);
        } else {
            try {
                setAuthIdPattern(Pattern.compile(regex));
            } catch (final PatternSyntaxException e) {
                // keep default pattern
                LOG.warn("auth-id pattern set via system property [{}] is not a valid regular expression",
                        RegistryManagementConstants.SYSTEM_PROPERTY_USERNAME_REGEX, e);
                setAuthIdPattern(RegistryManagementConstants.DEFAULT_PATTERN_USERNAME);
            }
        }
    }

    /**
     * Creates a new credentials object for an authentication identifier.
     *
     * @param authId The authentication identifier.
     * @param secrets The credential's secret password(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if auth ID does not match the configured regular expression or if secrets is empty.
     */
    public PasswordCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true)
            final String authId,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true)
            final List<PasswordSecret> secrets) {

        super(authId);
        setSecrets(secrets);
    }

    static void setAuthIdPattern(final Pattern pattern) {
        Objects.requireNonNull(pattern);
        PATTERN_AUTH_ID_VALUE = pattern;
        LOG.info("using regular expression for validating authentication identifiers: {}", pattern.pattern());
    }

    private static boolean validateAuthId(final String authId) {
        final Matcher matcher = PATTERN_AUTH_ID_VALUE.matcher(authId);
        if (matcher.matches()) {
            return true;
        } else {
            throw new IllegalArgumentException("authentication identifier must match regular expression: "
                    + PATTERN_AUTH_ID_VALUE.pattern());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return A predicate that matches the identifier against the auth-id pattern.
     */
    @Override
    protected Predicate<String> getAuthIdValidator() {
        return PasswordCredential::validateAuthId;
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
