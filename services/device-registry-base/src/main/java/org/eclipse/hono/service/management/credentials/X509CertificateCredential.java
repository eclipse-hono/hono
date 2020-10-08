/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.Strings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A credential type for storing the <a href="https://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a> formatted subject DN of
 * a client certificate.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#x-509-certificate">X.509 Certificate</a> for an
 * example of the configuration properties for this credential type.
 */
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
    public X509CertificateCredential(
            @JsonProperty(value = RegistryManagementConstants.FIELD_AUTH_ID, required = true) final String distinguishedName,
            @JsonProperty(value = RegistryManagementConstants.FIELD_SECRETS, required = true) final List<X509CertificateSecret> secrets) {
        super(new X500Principal(distinguishedName).getName(X500Principal.RFC2253));
        setSecrets(secrets);
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
