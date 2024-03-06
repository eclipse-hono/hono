/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.IdentityTemplate;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A trusted CA configuration.
 * <p>
 * Represents the <em>TrustedCA</em> schema object defined in the
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 *
 */
@RegisterForReflection
@JsonInclude(value = Include.NON_NULL)
public class TrustedCertificateAuthority {
    @JsonProperty(RegistryManagementConstants.FIELD_ID)
    private String id;

    private X500Principal subjectDn;

    private byte[] subjectDnBytes;

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

    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_ENABLED)
    private boolean autoProvisioningEnabled;

    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISION_AS_GATEWAY)
    private boolean autoProvisioningAsGatewayEnabled;

    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_DEVICE_ID_TEMPLATE)
    private String autoProvisioningDeviceIdTemplate;

    @JsonProperty(RegistryManagementConstants.FIELD_AUTH_ID_TEMPLATE)
    private String authIdTemplate;

    @JsonProperty(RegistryManagementConstants.FIELD_OCSP_REVOCATION_ENABLED)
    @JsonInclude(Include.NON_DEFAULT)
    private boolean ocspRevocationEnabled;

    @JsonProperty(RegistryManagementConstants.FIELD_OCSP_RESPONDER_URI)
    @JsonInclude(Include.NON_NULL)
    private String ocspResponderUri;

    @JsonProperty(RegistryManagementConstants.FIELD_OCSP_RESPONDER_CERT)
    @JsonInclude(Include.NON_NULL)
    private byte[] ocspResponderCert;

    @JsonProperty(RegistryManagementConstants.FIELD_OCSP_NONCE_ENABLED)
    @JsonInclude(Include.NON_DEFAULT)
    private boolean ocspNonceEnabled;

    /**
     * Checks if this object contains all required data.
     *
     * @return {@code true} if all required data is available.
     */
    @JsonIgnore
    public final boolean isValid() {
        if (ocspRevocationEnabled && cert == null && ocspResponderCert == null) {
            // No certificate for OCSP response verification
            return false;
        } else if (cert != null) {
            return true;
        } else if (subjectDn == null || publicKey == null || notBefore == null || notAfter == null) {
            return false;
        } else {
            try {
                final String alg = Optional.ofNullable(keyAlgorithm).orElse(CredentialsConstants.RSA_ALG);
                KeyFactory.getInstance(alg).generatePublic(new X509EncodedKeySpec(publicKey));
                return true;
            } catch (final GeneralSecurityException | IllegalArgumentException e) {
                return false;
            }
        }
    }

    /**
     * Gets the identifier of the trust anchor.
     *
     * @return the identifier of the trust anchor.
     */
    public final String getId() {
        return id;
    }

    /**
     * Sets the identifier of the trust anchor.
     *
     * @param id the identifier of the trust anchor.
     * @return A reference to this for fluent use.
     */
    public final TrustedCertificateAuthority setId(final String id) {
        this.id = id;
        return this;
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
     * Sets the subject of the trusted authority in binary format.
     *
     * @param subjectDnBytes The subject distinguished name encoded in ASN.1 DER format.
     * @return A reference to this for fluent use.
     */
    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN_BYTES)
    public final TrustedCertificateAuthority setSubjectDnBytes(final byte[] subjectDnBytes) {
        this.subjectDnBytes = subjectDnBytes;
        return this;
    }

    /**
     * Gets the subject of this trusted authority in binary form encoded in ASN.1 DER format.
     *
     * @return The subject distinguished name.
     */
    @JsonProperty(value = TenantConstants.FIELD_PAYLOAD_SUBJECT_DN_BYTES)
    @JsonInclude(Include.NON_NULL)
    public final byte[] getSubjectDnBytes() {
        return Optional.ofNullable(cert)
                .map(c -> c.getSubjectX500Principal().getEncoded())
                .orElse(subjectDnBytes);
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

    /**
     * Gets whether auto-provisioning of devices is enabled for this CA.
     *
     * @return {@code true} if auto-provisioning is enabled.
     */
    public final boolean isAutoProvisioningEnabled() {
        return autoProvisioningEnabled;
    }

    /**
     * Sets whether auto-provisioning of devices should be enabled for this CA.
     *
     * @param enabled {@code true} if auto-provisioning should be enabled.
     * @return A reference to this for fluent use.
     */
    public final TrustedCertificateAuthority setAutoProvisioningEnabled(final boolean enabled) {
        this.autoProvisioningEnabled = enabled;
        return this;
    }

    /**
     * Checks if any unregistered devices that authenticate with a client certificate issued by this CA
     * should be auto-provisioned as gateways.
     *
     * @return {@code true} if to be auto-provisioned as a gateway.
     */
    public final boolean isAutoProvisioningAsGatewayEnabled() {
        return autoProvisioningAsGatewayEnabled;
    }

    /**
     * Sets whether any unregistered devices that authenticate with a client certificate issued by this CA
     * should be auto-provisioned as gateways.
     *
     * @param enabled {@code true} if to be auto-provisioned as a gateway.
     * @return A reference to this for fluent use.
     */
    public final TrustedCertificateAuthority setAutoProvisioningAsGatewayEnabled(final boolean enabled) {
        this.autoProvisioningAsGatewayEnabled = enabled;
        return this;
    }

    /**
     * Gets the template used for generating the device identifier of the device/gateway 
     * being auto-provisioned.
     *
     * @return the template to use during auto-provisioning to generate device identifier.
     */
    public final String getAutoProvisioningDeviceIdTemplate() {
        return autoProvisioningDeviceIdTemplate;
    }

    /**
     * Sets the template used for generating the device identifier of the device/gateway being auto-provisioned.
     * <p>
     * The validity of the template is verified using {@link IdentityTemplate#checkValidity(String)}.
     *
     * @param template the template to use during auto-provisioning to generate device identifier.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the device id template is {@code null}.
     * @throws IllegalArgumentException if the template is not valid.
     */
    public final TrustedCertificateAuthority setAutoProvisioningDeviceIdTemplate(final String template) {
        IdentityTemplate.checkValidity(template);
        this.autoProvisioningDeviceIdTemplate = template;
        return this;
    }

    /**
     * Gets the template for generating the authentication identifier.
     *
     * @return the template for generating the authentication identifier.
     */
    public final String getAuthIdTemplate() {
        return this.authIdTemplate;
    }

    /**
     * Sets the template for generating the authentication identifier.
     * <p>
     * The validity of the template is verified using {@link IdentityTemplate#checkValidity(String)}.
     *
     * @param template the template for generating the authentication identifier.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the template is {@code null}.
     * @throws IllegalArgumentException if the template is not valid.
     */
    public final TrustedCertificateAuthority setAuthIdTemplate(final String template) {
        IdentityTemplate.checkValidity(template);
        this.authIdTemplate = template;
        return this;
    }

    /**
     * Gets whether OCSP revocation check is enabled. It is disabled by default.
     *
     * @return True if OCSP revocation is enabled.
     */
    public boolean isOcspRevocationEnabled() {
        return ocspRevocationEnabled;
    }

    /**
     * Sets whether OCSP revocation check is enabled. It is disabled by default. If OCSP is enabled then trusted CA
     * certificate or OCSP responder certificate must be explicitly set.
     *
     * @param ocspRevocationEnabled True if OCSP revocation check should be enabled.
     * @return A reference to this for fluent use.
     */
    public TrustedCertificateAuthority setOcspRevocationEnabled(final boolean ocspRevocationEnabled) {
        this.ocspRevocationEnabled = ocspRevocationEnabled;
        return this;
    }

    /**
     * Gets OCSP responder uri which will be used for OCSP revocation check of this trust anchor. If URI is null then
     * certificate AIA extension value should be used to obtain the values instead.
     *
     * @return OCSP responder URI or null.
     */
    public String getOcspResponderUri() {
        return ocspResponderUri;
    }

    /**
     * Sets OCSP responder uri which will be used for OCSP revocation check of this trust anchor. If URI is null then
     * certificate AIA extension value should be used to obtain the values instead.
     *
     * @param ocspResponderUri OCSP responder URI or null to use certificate AIA extension instead.
     * @return A reference to this for fluent use.
     */
    public TrustedCertificateAuthority setOcspResponderUri(final String ocspResponderUri) {
        this.ocspResponderUri = ocspResponderUri;
        return this;
    }

    /**
     * Gets OCSP responder certificate which is used to verify OCSP response signature. If custom certificate is not
     * set then issuing CA of client certificate is used instead.
     *
     * @return The DER encoded X.509 CA certificate used for OCSP signature verifications.
     * @throws CertificateException if the certificate cannot be deserialized into a byte array.
     */
    public byte[] getOcspResponderCert() throws CertificateException {
        if (ocspResponderCert != null) {
            return ocspResponderCert;
        } else if (ocspRevocationEnabled && cert != null) {
            return cert.getEncoded();
        } else {
            return null;
        }
    }

    /**
     * Sets OCSP responder certificate which is used to verify OCSP response signature. If custom certificate is not
     * set then issuing CA of client certificate is used instead.
     *
     * @param ocspResponderCert The Custom DER encoded X.509 CA certificate used for verification of OCSP response
     *                          signature or null to use client certificate issuing CA instead.
     * @return A reference to this for fluent use.
     */
    public TrustedCertificateAuthority setOcspResponderCert(final byte[] ocspResponderCert) {
        this.ocspResponderCert = ocspResponderCert;
        return this;
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
     * @return A reference to this for fluent use.
     */
    public TrustedCertificateAuthority setOcspNonceEnabled(final boolean ocspNonceEnabled) {
        this.ocspNonceEnabled = ocspNonceEnabled;
        return this;
    }
}
