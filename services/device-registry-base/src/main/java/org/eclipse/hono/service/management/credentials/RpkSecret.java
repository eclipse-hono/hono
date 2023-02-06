/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.Optional;

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
public final class RpkSecret extends CommonSecret {

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_KEY)
    private byte[] key;

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM)
    private String algorithm;

    /**
     * Sets the raw public key and algorithm for this secret.
     *
     * @param certificate The certificate from which the public key is extracted.
     * @return A reference to this for fluent use.
     * @throws CertificateException if the byte array cannot be deserialized into an X.509 certificate.
     */
    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_CERT)
    public RpkSecret setCertificate(final byte[] certificate) throws CertificateException {
        Objects.requireNonNull(certificate);

        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final var cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificate));
        setKey(cert.getPublicKey().getEncoded());
        setAlgorithm(cert.getPublicKey().getAlgorithm());
        setNotBefore(cert.getNotBefore().toInstant());
        setNotAfter(cert.getNotAfter().toInstant());
        return this;
    }

    /**
     * Gets the public key used by this certificate authority.
     *
     * @return The DER encoded public key.
     */
    public byte[] getKey() {

      return key;
    }

    /**
     * Sets the raw public key.
     *
     * @param key The DER encoded public key.
     * @return S reference to this for fluent use.
     * @throws NullPointerException if the key is {@code null}.
     */
    public RpkSecret setKey(final byte[] key) {
        Objects.requireNonNull(key);
        this.key = key;
        return this;
    }

    /**
     * Gets the name of the algorithm used in the creation of the raw public key.
     *
     * @return The name of the algorithm.
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Sets the name of the algorithm used in the creation of the raw public key.
     *
     * @param algorithm The name of the algorithm.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if algorithm is neither {@value CredentialsConstants#RSA_ALG} nor
     *             {@value CredentialsConstants#EC_ALG}.
     */
    public RpkSecret setAlgorithm(final String algorithm) {
        Objects.requireNonNull(algorithm);

        this.algorithm = algorithm;
        return this;
    }

    /**
     * Checks if the key can be deserialized from its DER encoding.
     *
     * @throws IllegalStateException if key or algorithm are {@code null} or if the key
     *                               cannot be deserialized.
     */
    @Override
    protected void checkValidityOfSpecificProperties() {
        if (key == null || algorithm == null) {
            throw new IllegalStateException("secret requires key and algorithm");
        } else {
            try {
                final String alg = Optional.ofNullable(algorithm).orElse(CredentialsConstants.RSA_ALG);
                KeyFactory.getInstance(alg).generatePublic(new X509EncodedKeySpec(key));
            } catch (final GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException("key cannot be deserialized", e);
            }
        }
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add(RegistryManagementConstants.FIELD_SECRETS_KEY, key)
                .add(RegistryManagementConstants.FIELD_SECRETS_ALGORITHM, algorithm);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets this secret's algorithm and key properties to the values of the other secret's corresponding properties if this
     * secret's algorithm and key properties are {@code null}.
     */
    @Override
    protected void mergeProperties(final CommonSecret otherSecret) {

        Objects.requireNonNull(otherSecret);

        if (containsOnlySecretId()) {
            final RpkSecret otherRpkSecret = (RpkSecret) otherSecret;
            this.setAlgorithm(otherRpkSecret.getAlgorithm());
            this.setKey(otherRpkSecret.getKey());
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
                && Strings.isNullOrEmpty(algorithm)
                && Strings.isNullOrEmpty(key));
    }
}
