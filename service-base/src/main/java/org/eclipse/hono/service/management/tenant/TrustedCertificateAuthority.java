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

import org.eclipse.hono.util.TenantConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A trusted CA configuration.
 *
 */
@JsonInclude(value = Include.NON_NULL)
public class TrustedCertificateAuthority {

    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, required = true)
    private String subjectDn;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY)
    private byte[] publicKey;

    @JsonProperty(TenantConstants.FIELD_PAYLOAD_CERT)
    private byte[] certificate;

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
     * Sets the cert configuration property.
     *
     * @param certificate The cert property of the trusted root certificate.
     * @return            a reference to this for fluent use.
     */
    public TrustedCertificateAuthority setCertificate(final byte[] certificate) {
        this.certificate = certificate;
        return this;
    }

    public byte[] getCertificate() {
        return certificate;
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
