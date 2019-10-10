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
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.TenantConstants;

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

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY)
    private byte[] publicKey;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM)
    private String keyAlgorithm;

    /**
     * Sets the subject of the trusted authority.
     *
     * @param subjectDn The subject distinguished name.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the subject DN is invalid.
     */
    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, required = true)
    public final TrustedCertificateAuthority setSubjectDn(final String subjectDn) {
        this.subjectDn = new X500Principal(subjectDn);
        return this;
    }

    /**
     * Gets the subject of this trusted authority.
     *
     * @return The subject distinguished name.
     */
    public final X500Principal getSubjectDn() {
        return subjectDn;
    }

    /**
     * Gets this trusted authority's subject formatted as a string in RFC 2253 format.
     *
     * @return The subject distinguished name.
     */
    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, required = true)
    public final String getSubjectDnAsString() {
        return subjectDn.getName(X500Principal.RFC2253);
    }

    /**
     * Sets the public key used by this certificate authority.
     *
     * @param publicKey The DER encoded public key.
     * @return A reference to this for fluent use.
     */
    public final TrustedCertificateAuthority setPublicKey(final byte[] publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    /**
     * Gets the public key used by this certificate authority.
     *
     * @return The DER encoded public key.
     */
    public final byte[] getPublicKey() {
        return publicKey;
    }

    /**
     * Sets the public key, key algorithm and subject DN from an X.509 certificate.
     *
     * @param certificate The DER encoded X.509 certificate.
     * @return A reference to this for fluent use.
     * @throws CertificateException if the byte array cannot be deserialized into an X.509 certificate.
     */
    @JsonProperty(TenantConstants.FIELD_PAYLOAD_CERT)
    public final TrustedCertificateAuthority setCertificate(final byte[] certificate) throws CertificateException {

        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        final X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certificate));
        this.subjectDn = cert.getSubjectX500Principal();
        this.keyAlgorithm = cert.getPublicKey().getAlgorithm();
        this.publicKey = cert.getPublicKey().getEncoded();
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
        return keyAlgorithm;
    }
}
