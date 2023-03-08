/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import java.util.List;
import java.util.function.Predicate;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.util.Strings;

/**
 * An X.509 credential type, where authId is <a href="https://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a> formatted
 * subject DN.
 */
public class X509CertificateCredentialWithSubjectDn extends X509CertificateCredential {

    /**
     * Creates a new credentials object with an X.500 Distinguished Name.
     * <p>
     * The given distinguished name will be normalized to RFC 2253 format.
     *
     * @param distinguishedName The DN to use as the authentication identifier.
     * @param secrets The credential's secret(s).
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if the given string is not a valid X.500 distinguished name or if secrets is
     *             empty.
     */
    protected X509CertificateCredentialWithSubjectDn(final String distinguishedName,
            final List<X509CertificateSecret> secrets) {
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
}
