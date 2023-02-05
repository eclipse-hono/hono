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

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
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
public final class RpkSecret extends CommonSecret {

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_KEY)
    private byte[] key;

    private String algorithm;

    /**
     * Sets the raw public key and algorithm for this secret.
     *
     * @param certificate the certificate from which the public key is extracted.
     * @return a reference to this for fluent use.
     * @throws CertificateException if the provided certificate is not valid.
     */
    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_CERT)
    public RpkSecret setCertificate(final byte[] certificate) throws CertificateException {
        Objects.requireNonNull(certificate);

        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final X509Certificate cert = (X509Certificate) factory
                .generateCertificate(new ByteArrayInputStream(certificate));
        final PublicKey publicKey = cert.getPublicKey();
        setAlgorithm(publicKey.getAlgorithm());
        this.key = publicKey.getEncoded();
        return this;
    }

    public byte[] getKey() {
        return key;
    }

    /**
     * Sets the raw public key and algorithm for this secret.
     *
     * @param key the raw public key.
     * @return a reference to this for fluent use.
     * @throws RuntimeException if the provided raw public key is not valid.
     */
    public RpkSecret setKey(final byte[] key) {
        Objects.requireNonNull(key);

        final PublicKey publicKey = convertToPublicKey(key);
        setAlgorithm(publicKey.getAlgorithm());
        this.key = key;
        return this;
    }

    private PublicKey convertToPublicKey(final byte[] encodedPublicKey) {
        final X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(encodedPublicKey);
        KeyFactory keyFactory;
        PublicKey publicKey;
        try {
            keyFactory = KeyFactory.getInstance(CredentialsConstants.RSA_ALG);
            publicKey = keyFactory.generatePublic(keySpecX509);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            try {
                keyFactory = KeyFactory.getInstance(CredentialsConstants.EC_ALG);
                publicKey = keyFactory.generatePublic(keySpecX509);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
                throw new RuntimeException("Key is invalid or algorithm not supported.");
            }
        }
        return publicKey;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Sets the name of the algorithm used in the creation of the raw public key.
     *
     * @param algorithm the name of the algorithm.
     * @return a reference to this for fluent use.
     * @throws IllegalArgumentException if {@code alg} is not either {@value CredentialsConstants#RSA_ALG} or
     *             {@value CredentialsConstants#EC_ALG}.
     */
    public RpkSecret setAlgorithm(final String algorithm) {
        Objects.requireNonNull(algorithm);

        this.algorithm = algorithm;
        return this;
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
     * Sets this secret's alg and key properties to the values of the other secret's corresponding properties if this
     * secret's alg and key properties are {@code null}.
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
