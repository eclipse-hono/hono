/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.tenant;

import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A trusted CA configuration.
 * <p>
 * Represents the <em>TrustedCA</em> schema object defined in the
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 *
 */
@JsonInclude(value = Include.NON_NULL)
public class TrustedCertificateAuthority {

    private X500Principal subjectDn;

    private byte[] publicKey;

    private X509Certificate cert;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM)
    private String keyAlgorithm;

    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE)
    @HonoTimestamp
    private Instant notBefore;
    @JsonProperty(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER)
    @HonoTimestamp
    private Instant notAfter;

    /**
     * Checks if this object contains all required data.
     * 
     * @return {@code true} if all required data is available.
     */
    @JsonIgnore
    public final boolean isValid() {
        if (cert != null) {
            return true;
        } else if (subjectDn == null || publicKey == null || notBefore == null || notAfter == null) {
            return false;
        } else {
            try {
                final String alg = Optional.ofNullable(keyAlgorithm).orElse("RSA");
                KeyFactory.getInstance(alg).generatePublic(new X509EncodedKeySpec(publicKey));
                return true;
            } catch (final GeneralSecurityException | IllegalArgumentException e) {
                return false;
            }
        }
    }

    /**
     * Sets the subject of the trusted authority.
     *
     * @param subjectDn The subject distinguished name.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the subject DN is invalid.
     */
    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN)
    public final TrustedCertificateAuthority setSubjectDn(final String subjectDn) {
        setSubjectDn(new X500Principal(subjectDn));
        return this;
    }

    /**
     * Sets the subject of the trusted authority.
     *
     * @param subjectDn The subject distinguished name.
     * @return A reference to this for fluent use.
     */
    public final TrustedCertificateAuthority setSubjectDn(final X500Principal subjectDn) {
        this.subjectDn = subjectDn;
        return this;
    }

    /**
     * Gets the subject of this trusted authority.
     *
     * @return The subject distinguished name.
     */
    public final X500Principal getSubjectDn() {
        return Optional.ofNullable(cert)
                .map(c -> c.getSubjectX500Principal())
                .orElse(subjectDn);
    }

    /**
     * Gets this trusted authority's subject formatted as a string in RFC 2253 format.
     *
     * @return The subject distinguished name or {@code null} if not set.
     */
    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN)
    public final String getSubjectDnAsString() {

        return Optional.ofNullable(cert)
                .map(c -> c.getSubjectX500Principal().getName(X500Principal.RFC2253))
                .orElseGet(() -> Optional.ofNullable(subjectDn)
                    .map(s -> s.getName(X500Principal.RFC2253))
                    .orElse(null));
    }

    /**
     * Sets the public key used by this certificate authority.
     *
     * @param publicKey The DER encoded public key.
     * @return A reference to this for fluent use.
     */
    @JsonProperty(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY)
    public final TrustedCertificateAuthority setPublicKey(final byte[] publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    /**
     * Gets the public key used by this certificate authority.
     *
     * @return The DER encoded public key.
     */
    @JsonProperty(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY)
    public final byte[] getPublicKey() {

        return Optional.ofNullable(cert)
                .map(c -> c.getPublicKey().getEncoded())
                .orElse(publicKey);
    }

    /**
     * Sets the trusted certificate authority.
     *
     * @param certificate The DER encoded X.509 certificate.
     * @return A reference to this for fluent use.
     * @throws CertificateException if the byte array cannot be deserialized into an X.509 certificate.
     */
    @JsonProperty(TenantConstants.FIELD_PAYLOAD_CERT)
    public final TrustedCertificateAuthority setCertificate(final byte[] certificate) throws CertificateException {

        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificate));
        return this;
    }

    /**
     * Sets the algorithm used by this authority's public key.
     * 
     * @param keyAlgorithm The name of the algorithm.
     * @return A reference to this for fluent use.
     */
    public final TrustedCertificateAuthority setKeyAlgorithm(final String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
        return this;
    }

    /**
     * Gets the algorithm used by this authority's public key.
     * 
     * @return The name of the algorithm.
     */
    public final String getKeyAlgorithm() {

        return Optional.ofNullable(cert)
                .map(c -> c.getPublicKey().getAlgorithm())
                .orElse(keyAlgorithm);
    }

    /**
     * Sets the earliest instant in time that this CA may be used for authenticating a device.
     *
     * @param notBefore The instant.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the value is {@code null}.
     */
    public final TrustedCertificateAuthority setNotBefore(final Instant notBefore) {
        this.notBefore = Objects.requireNonNull(notBefore);
        return this;
    }

    /**
     * Gets the earliest instant in time that this CA may be used for authenticating a device.
     *
     * @return The instant or {@code null} if not set.
     */
    public final Instant getNotBefore() {
        return Optional.ofNullable(cert)
                .map(cert -> cert.getNotBefore().toInstant())
                .orElse(notBefore);
    }

    /**
     * Sets the latest instant in time that this CA may be used for authenticating a device.
     *
     * @param notAfter The instant.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the value is {@code null}.
     */
    public final TrustedCertificateAuthority setNotAfter(final Instant notAfter) {
        this.notAfter = Objects.requireNonNull(notAfter);
        return this;
    }

    /**
     * Gets the latest instant in time that this CA may be used for authenticating a device.
     * 
     * @return The instant or {@code null} if not set.
     */
    public final Instant getNotAfter() {
        return Optional.ofNullable(cert)
                .map(cert -> cert.getNotAfter().toInstant())
                .orElse(notAfter);
    }
}
