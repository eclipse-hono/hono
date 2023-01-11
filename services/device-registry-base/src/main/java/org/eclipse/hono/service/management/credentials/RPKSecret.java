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

import java.util.Objects;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This class encapsulates the secrets information for a raw public key credentials type.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class RPKSecret extends CommonSecret {

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_KEY)
    private String key;

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM)
    private String alg;

    public final String getKey() {
        return key;
    }

    /**
     * Sets the raw public key value for this secret.
     *
     * @param key the raw public key.
     * @return a reference to this for fluent use.
     * @throws IllegalArgumentException if the raw public key does not start and end with either
     *             {@value CredentialsConstants#BEGIN_KEY} and {@value CredentialsConstants#END_KEY} or
     *             {@value CredentialsConstants#BEGIN_CERT} and {@value CredentialsConstants#END_CERT}.
     */
    public final RPKSecret setKey(final String key) {
        Objects.requireNonNull(key);
        if (!(key.contains(CredentialsConstants.BEGIN_KEY) && key.contains(CredentialsConstants.END_KEY))
                && !(key.contains(CredentialsConstants.BEGIN_CERT) && key.contains(CredentialsConstants.END_CERT))) {
            throw new IllegalArgumentException(
                    String.format("key must start and end with either \"%s\" and \"%s\" or \"%s\" and \"%s\".",
                            CredentialsConstants.BEGIN_KEY, CredentialsConstants.END_KEY,
                            CredentialsConstants.BEGIN_CERT, CredentialsConstants.END_CERT));
        }
        this.key = key;
        return this;
    }

    public final String getAlg() {
        return alg;
    }

    /**
     * Sets the name of the algorithm used in the creation of the raw public key.
     *
     * @param alg the name of the algorithm.
     * @return a reference to this for fluent use.
     * @throws IllegalArgumentException if {@code alg} is not either {@value CredentialsConstants#RSA_ALG} or
     *             {@value CredentialsConstants#EC_ALG}.
     */
    public final RPKSecret setAlg(final String alg) {
        Objects.requireNonNull(alg);
        if (!alg.equals(CredentialsConstants.RSA_ALG) && !alg.equals(CredentialsConstants.EC_ALG)) {
            throw new IllegalArgumentException(
                    String.format("alg must be either \"%s\" or \"%s\".", CredentialsConstants.RSA_ALG,
                            CredentialsConstants.EC_ALG));
        }
        this.alg = alg;
        return this;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add(RegistryManagementConstants.FIELD_SECRETS_KEY, key)
                .add(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM, alg);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets this secret's alg and key properties to the values of the other secret's corresponding properties if this
     * secret's alg and key properties are {@code null}.
     */
    @Override
    protected void mergeProperties(final CommonSecret otherSecret) {

        Objects.requireNonNull(otherSecret);

        if (containsOnlySecretId()) {
            final RPKSecret otherRPKSecret = (RPKSecret) otherSecret;
            this.setAlg(otherRPKSecret.getAlg());
            this.setKey(otherRPKSecret.getKey());
        }
    }

    /**
     * Checks if this secret contains an identifier only.
     *
     * @return {@code true} if the <em>id</em> property is not {@code null} and the <em>alg</em> and <em>key</em>
     *         properties are {@code null}.
     */
    public boolean containsOnlySecretId() {
        return (!Strings.isNullOrEmpty(getId())
                && Strings.isNullOrEmpty(alg)
                && Strings.isNullOrEmpty(key));
    }
}
