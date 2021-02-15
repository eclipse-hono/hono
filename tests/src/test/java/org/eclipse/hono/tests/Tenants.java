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

package org.eclipse.hono.tests;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;

/**
 * Helper class for working with tenants.
 */
public final class Tenants {

    private Tenants() {
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param cert The trust anchor.
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final X509Certificate cert) {
        return createTenantForTrustAnchor(cert.getSubjectX500Principal(), cert.getPublicKey());
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final X500Principal subjectDn, final PublicKey publicKey) {
        return createTenantForTrustAnchor(
                subjectDn.getName(X500Principal.RFC2253),
                publicKey.getEncoded(),
                publicKey.getAlgorithm());
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     * @param algorithm The public key algorithm.
     *
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final String subjectDn, final byte[] publicKey, final String algorithm) {
        return createTenantForTrustAnchor(
                subjectDn,
                publicKey,
                algorithm,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS));
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     * @param algorithm The public key algorithm.
     * @param notBefore The earliest instant that the trust anchor may be used.
     * @param notAfter The latest instant that the trust anchor may be used.
     *
     * @return The new tenant. Never returns {@code null}.
     * @throws NullPointerException if any of the arguments other than algorithm are {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(
            final String subjectDn,
            final byte[] publicKey,
            final String algorithm,
            final Instant notBefore,
            final Instant notAfter) {

        final TrustedCertificateAuthority trustAnchor = createTrustAnchor(null, subjectDn, publicKey, algorithm,
                notBefore, notAfter);
        return new Tenant().setTrustedCertificateAuthorities(List.of(trustAnchor));
    }

    /**
     * Create a trust anchor.
     *
     * @param id The trust anchor ID.
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     * @param algorithm The public key algorithm.
     * @param notBefore The earliest instant that the trust anchor may be used.
     * @param notAfter The latest instant that the trust anchor may be used.
     *
     * @return The trust anchor. Never returns {@code null}.
     * @throws NullPointerException if any of the arguments other than algorithm are {@code null}.
     */
    public static TrustedCertificateAuthority createTrustAnchor(
            final String id,
            final String subjectDn,
            final byte[] publicKey,
            final String algorithm,
            final Instant notBefore,
            final Instant notAfter) {

        final var trustedCa = new TrustedCertificateAuthority()
                .setSubjectDn(subjectDn)
                .setPublicKey(publicKey)
                .setNotBefore(notBefore)
                .setNotAfter(notAfter);
        Optional.ofNullable(algorithm).ifPresent(trustedCa::setKeyAlgorithm);
        Optional.ofNullable(id).ifPresent(trustedCa::setId);

        return trustedCa;
    }
}
