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
import java.time.Instant;

import org.eclipse.hono.annotation.HonoTimestamp;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A trusted CA configuration.
 *
 */
@JsonInclude(value = Include.NON_NULL)
public final class TrustedCertificateAuthority {

    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, required = true)
    private String subjectDn;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY)
    private byte[] publicKey;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_NOT_BEFORE)
    @HonoTimestamp
    private Instant notBefore;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_NOT_AFTER)
    @HonoTimestamp
    private Instant notAfter;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_KEY_ALGORITHM)
    private String keyAlgorithm;

    /**
     * Sets the subject-dn configuration property.
     *
     * @param subjectDn The subject-dn property to set.
     * @return          a reference to this for fluent use.
     */
    public TrustedCertificateAuthority setSubjectDn(final String subjectDn) {
        this.subjectDn = subjectDn;
        return this;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    /**
     * Sets the public-key configuration property.
     *
     * @param publicKey The public-key property of the trusted root certificate.
     * @return          a reference to this for fluent use.
     */
    public TrustedCertificateAuthority setPublicKey(final byte[] publicKey) {
        this.publicKey = publicKey;
        return this;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    /**
     * This method constructs an X509 certificate and uses it to set
     * the <em>subject-dn</em>, <em>public-key</em>, <em>algorithm</em>,
     * <em>not-before</em> and <em>not-after</em> properties of this
     * trusted certificate authority.
     *<p>
     * The <em>cert</em> property is not included in the JSON payload sent to the 
     * tenant service.
     * 
     * @param certificate The cert property of the trusted root certificate.
     * @return            a reference to this for fluent use.
     * @throws IllegalArgumentException if an X509 certificate cannot be created
     *         from the given Base64 encoding.
     */
    @JsonSetter(RegistryManagementConstants.FIELD_PAYLOAD_CERT)
    public TrustedCertificateAuthority setCertificate(final byte[] certificate) {
        final ByteArrayInputStream is = new ByteArrayInputStream(certificate);
        try {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            final X509Certificate x509Cert = (X509Certificate) factory.generateCertificate(is);

            setSubjectDn(x509Cert.getSubjectDN().getName());
            setPublicKey(x509Cert.getPublicKey().getEncoded());
            setKeyAlgorithm(x509Cert.getPublicKey().getAlgorithm());
            setNotBefore(x509Cert.getNotBefore().toInstant());
            setNotAfter(x509Cert.getNotAfter().toInstant());

        } catch (final CertificateException e) {
            throw new IllegalArgumentException("Invalid encoding of an X509 certificate authority");
        }
        return this;
    }

    /**
     * Sets the not-before property.
     * 
     * @param notBefore The not-before value to assign.
     * 
     * @return A reference to this API so it can be used fluently.
     */
    public TrustedCertificateAuthority setNotBefore(final Instant notBefore) {
        this.notBefore = notBefore;
        return this;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    /**
     * Sets the not-after property.
     * 
     * @param notAfter The not-after value to assign.
     *
     * @return A reference to this API so it can be used fluently.
     */
    public TrustedCertificateAuthority setNotAfter(final Instant notAfter) {
        this.notAfter = notAfter;
        return this;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    /**
     * Sets the algorithm property of the trusted root certificate.
     * 
     * @param keyAlgorithm  The algorithm name of the public key.
     * @return              a reference to this for fluent use.
     */
    public TrustedCertificateAuthority setKeyAlgorithm(final String keyAlgorithm) {
        this.keyAlgorithm = keyAlgorithm;
        return this;
    }

    public String getKeyAlgorithm() {
        return keyAlgorithm;
    }
}
