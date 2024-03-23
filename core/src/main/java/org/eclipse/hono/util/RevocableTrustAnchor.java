/*******************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * This call enhances {@link TrustAnchor} class to additional revocation properties. It defines properties for
 * enabling and configuring revocation check during verification of client certificates for particular trust anchor.
 * Currently only OCSP revocation is supported.
 */
public class RevocableTrustAnchor extends TrustAnchor {

    private static final Logger LOG = LoggerFactory.getLogger(RevocableTrustAnchor.class);

    private boolean ocspEnabled;
    private URI ocspResponderUri;
    private X509Certificate ocspResponderCert;
    private boolean ocspNonceEnabled;

    /**
     * See {@link TrustAnchor}.
     *
     * @param caPrincipal See {@link TrustAnchor}.
     * @param pubKey See {@link TrustAnchor}.
     * @param nameConstraints See {@link TrustAnchor}.
     */
    public RevocableTrustAnchor(final X500Principal caPrincipal, final PublicKey pubKey, final byte[] nameConstraints) {
        super(caPrincipal, pubKey, nameConstraints);
    }

    /**
     * See {@link TrustAnchor}.
     *
     * @param caPrincipal See {@link TrustAnchor}.
     * @param pubKey See {@link TrustAnchor}.
     * @param nameConstraints See {@link TrustAnchor}.
     * @param trustedCAProps {@link JsonObject} containing revocation properties of trusted
     *                       certification authority.
     */
    public RevocableTrustAnchor(final X500Principal caPrincipal, final PublicKey pubKey, final byte[] nameConstraints,
        final JsonObject trustedCAProps) {
        super(caPrincipal, pubKey, nameConstraints);
        setRevocationProperties(trustedCAProps);
    }

    private void setRevocationProperties(final JsonObject keyProps) {
        ocspEnabled = JsonHelper.getValue(keyProps, TenantConstants.FIELD_OCSP_REVOCATION_ENABLED, Boolean.class, false);
        ocspNonceEnabled = JsonHelper.getValue(keyProps, TenantConstants.FIELD_OCSP_REVOCATION_ENABLED, Boolean.class, false);
        final String ocspResponderUriString = JsonHelper.getValue(keyProps, TenantConstants.FIELD_OCSP_RESPONDER_URI, String.class, null);
        if (ocspResponderUriString != null) {
            ocspResponderUri = URI.create(ocspResponderUriString);
        }
        final byte[] ocspResponderCertData = JsonHelper.getValue(keyProps, TenantConstants.FIELD_OCSP_RESPONDER_CERT, byte[].class, null);
        if (ocspResponderCertData != null) {
            try {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                ocspResponderCert = (X509Certificate) certificateFactory.generateCertificate(
                        new ByteArrayInputStream(ocspResponderCertData));
            } catch (CertificateException e) {
                LOG.error("failed to parse OCSP responder certificate", e);
            }
        }
        ocspNonceEnabled = JsonHelper.getValue(keyProps, TenantConstants.FIELD_OCSP_NONCE_ENABLED, Boolean.class, false);
    }

    /**
     * Gets whether OCSP revocation check is enabled. It is disabled by default.
     *
     * @return True if OCSP revocation is enabled.
     */
    public boolean isOcspEnabled() {
        return ocspEnabled;
    }

    /**
     * Sets whether OCSP revocation check is enabled. It is disabled by default.
     *
     * @param ocspEnabled True if OCSP revocation check should be enabled.
     */
    public void setOcspEnabled(final boolean ocspEnabled) {
        this.ocspEnabled = ocspEnabled;
    }

    /**
     * Gets OCSP responder uri which will be used for OCSP revocation check of this trust anchor. If URI is null then
     * certificate AIA extension value should be used to obtain the values instead.
     *
     * @return OCSP responder URI or null.
     */
    public URI getOcspResponderUri() {
        return ocspResponderUri;
    }

    /**
     * Sets OCSP responder uri which will be used for OCSP revocation check of this trust anchor. If URI is null then
     * certificate AIA extension value should be used to obtain the values instead.
     *
     * @param ocspResponderUri OCSP responder URI or null to use certificate AIA extension instead.
     */
    public void setOcspResponderUri(final URI ocspResponderUri) {
        this.ocspResponderUri = ocspResponderUri;
    }

    /**
     * Gets OCSP responder certificate which is used to verify OCSP response signature. If custom certificate is not
     * set then issuing CA of client certificate is used to verify the signature.
     *
     * @return The certificate used for OCSP signature verifications.
     */
    public X509Certificate getOcspResponderCert() {
        return ocspResponderCert;
    }

    /**
     * Sets OCSP responder certificate which is used to verify OCSP response signature. If custom certificate is not
     * set then issuing CA of client certificate is used to verify the signature.
     *
     * @param ocspResponderCert Custom CA certificate used for verification of OCSP response signature or null to use client
     *                          certificate issuing CA instead.
     */
    public void setOcspResponderCert(final X509Certificate ocspResponderCert) {
        this.ocspResponderCert = ocspResponderCert;
    }

    /**
     * Gets whether nonce extension should be sent in OCSP request. Nonce is important to avoid replay attacks but can
     * increase resource usage. It is disabled by default.
     *
     * @return True if nonce extension is enabled.
     */
    public boolean isOcspNonceEnabled() {
        return ocspNonceEnabled;
    }

    /**
     * Sets whether nonce extension should be sent in OCSP request. Nonce is important to avoid replay attacks but can
     * increase resource usage. It is disabled by default.
     *
     * @param ocspNonceEnabled True to enable nonce extension in OCSP requests.
     */
    public void setOcspNonceEnabled(final boolean ocspNonceEnabled) {
        this.ocspNonceEnabled = ocspNonceEnabled;
    }
}
