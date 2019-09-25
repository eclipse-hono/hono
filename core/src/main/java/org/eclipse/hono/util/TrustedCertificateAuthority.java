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

package org.eclipse.hono.util;

import java.time.Instant;

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Encapsulates a trusted CA configuration stored in the device registry.
 *
 */
@JsonInclude(value = Include.NON_NULL)
public final class TrustedCertificateAuthority {

    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, required = true)
    private String subjectDn;

    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, required = true)
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

    /**
     * Checks if this trusted certificate is valid.
     * 
     * @return {@code true} if the certificate is valid or {@code false} otherwise.
     */
    @JsonIgnore
    public boolean isValid() {
        if (notBefore == null || notAfter == null) {
            return false;
        } else {
            final Instant now = Instant.now();
            return !(now.isBefore(notBefore) || now.isAfter(notAfter));
        }
    }
}
