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

import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Objects;

import org.eclipse.hono.service.auth.ExternalJwtAuthTokenValidator;
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
public final class RPKSecret extends CommonSecret {

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_KEY)
    private byte[] key;

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM)
    private String alg;

    public byte[] getKey() {
        return key;
    }

    /**
     * Sets the raw public key value for this secret.
     *
     * @param key the raw public key or certificate.
     * @return a reference to this for fluent use.
     * @throws RuntimeException if the provided raw public key or certificate is not valid.
     */
    public RPKSecret setKey(final String key) {
        Objects.requireNonNull(key);

        final ExternalJwtAuthTokenValidator authTokenValidator = new ExternalJwtAuthTokenValidator();
        PublicKey publicKey;
        try {
            publicKey = authTokenValidator.convertX509CertStringToPublicKey(key);
        } catch (CertificateException e) {
            publicKey = authTokenValidator.convertPublicKeyStringToPublicKey(key);
        }
        this.key = publicKey.getEncoded();
        return this;
    }

    public String getAlg() {
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
    public RPKSecret setAlg(final String alg) {
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
            this.setKey(Base64.getEncoder().encodeToString(otherRPKSecret.getKey()));
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
