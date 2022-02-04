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
package org.eclipse.hono.service.management.credentials;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A credential type for storing the <a href="https://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a> formatted subject DN of
 * a client certificate.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#x-509-certificate">X.509 Certificate</a> for an
 * example of the configuration properties for this credential type.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class X509CertificateCredential extends CommonCredential {

    static final String TYPE = RegistryManagementConstants.SECRETS_TYPE_X509_CERT;

    private final List<X509CertificateSecret> secrets = new LinkedList<>();

    /**
     * Creates a new credentials object for an X.500 Distinguished Name.
     * <p>
     * The given distinguished name will be normalized to RFC 2253 format.
     *
     * @param distinguishedName The DN to use as the authentication identifier.
     * @param secrets The credential's secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the given string is not a valid X.500 distinguished name or if
     *                                  secrets is empty.
     */
    private X509CertificateCredential(final String distinguishedName, final List<X509CertificateSecret> secrets) {
        super(new X500Principal(distinguishedName).getName(X500Principal.RFC2253));
        setSecrets(secrets);
    }

    /**
     * Creates a new credentials object.
     * <p>
     * This method tries to decode a non-null byte array into an X.509 certificate and delegate to
     * {@link #fromCertificate(X509Certificate)}. Otherwise, the {@link #fromSubjectDn(String, List)}
     * method is invoked with the given distinguished name and secrets parameter values.
     *
     * @param derEncodedX509Certificate The DER encoding of the client certificate.
     * @param distinguishedName The DN to use as the authentication identifier.
     * @param secrets The credential's secret(s).
     * @throws NullPointerException if certificate bytes and any of distinguished name and secrets are {@code null}.
     * @throws IllegalArgumentException if the given byte array cannot be decoded into an X.509 certificate or if
     *                                  the given name is not a valid X.500 distinguished name or if
     *                                  secrets is empty.
     * @return The credentials.
     */
    @JsonCreator(mode = Mode.PROPERTIES)
    public static X509CertificateCredential fromProperties(
            @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_CERT) final byte[] derEncodedX509Certificate,
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID) final String distinguishedName,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS) final List<X509CertificateSecret> secrets) {

        if (derEncodedX509Certificate == null) {
            Objects.requireNonNull(distinguishedName);
            Objects.requireNonNull(secrets);
            if (secrets.size() != 1) {
                throw new IllegalArgumentException("list must contain exactly one secret");
            }
            return fromSubjectDn(distinguishedName, secrets);
        } else {
            return fromCertificate(deserialize(derEncodedX509Certificate));
        }
    }

    /**
     * Creates a new credentials object for an X.500 Distinguished Name.
     * <p>
     * The given distinguished name will be normalized to RFC 2253 format.
     *
     * @param distinguishedName The DN to use as the authentication identifier.
     * @param secrets The credential's secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the given string is not a valid X.500 distinguished name or if
     *                                  secrets is empty.
     * @return The credentials.
     */
    public static X509CertificateCredential fromSubjectDn(
            final String distinguishedName,
            final List<X509CertificateSecret> secrets) {

        return new X509CertificateCredential(distinguishedName, secrets);
    }

    /**
     * Creates a new credentials object.
     *
     * @param certificate The X.509 certificate.
     * @return The credentials.
     * @throws NullPointerException if certificate is {@code null}.
     */
    public static X509CertificateCredential fromCertificate(final X509Certificate certificate) {

        Objects.requireNonNull(certificate);
        final var secret = new X509CertificateSecret();
        secret.setNotBefore(certificate.getNotBefore().toInstant());
        secret.setNotAfter(certificate.getNotAfter().toInstant());
        return new X509CertificateCredential(
                certificate.getSubjectX500Principal().getName(X500Principal.RFC2253),
                List.of(secret));
    }

    private static X509Certificate deserialize(final byte[] base64EncodedX509Certificate) {

        try {
            final CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(base64EncodedX509Certificate));
        } catch (final CertificateException e) {
            throw new IllegalArgumentException("cannot deserialize X.509 certificate", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Predicate<String> getAuthIdValidator() {
        return authId -> {
            if (Strings.isNullOrEmpty(authId)) {
                return false;
            }
            final X500Principal distinguishedName = new X500Principal(authId);
            return distinguishedName.getName(X500Principal.RFC2253).equals(authId);
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsonIgnore
    public final String getType() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @return An unmodifiable list of secrets.
     */
    @Override
    @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS)
    public final List<X509CertificateSecret> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    /**
     * Sets the list of X509 certificate secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to the validity period of the certificate.
     *
     * @param secrets The secret to set.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if secrets is {@code null}.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public final X509CertificateCredential setSecrets(final List<X509CertificateSecret> secrets) {
        Objects.requireNonNull(secrets);
        if (secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets cannot be empty");
        }
        this.secrets.clear();
        this.secrets.addAll(secrets);
        return this;
    }
}
