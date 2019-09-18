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
import java.time.Period;
import java.util.ArrayList;

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
        final String subjectDn = cert.getSubjectX500Principal().getName(X500Principal.RFC2253);
        final PublicKey key = cert.getPublicKey();
        final Instant notBefore = cert.getNotBefore().toInstant();
        final Instant notAfter = cert.getNotAfter().toInstant();
        return createTenantForTrustAnchor(subjectDn, key.getEncoded(), key.getAlgorithm(), notBefore, notAfter);
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     *
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final X500Principal subjectDn, final PublicKey publicKey) {
        return createTenantForTrustAnchor(subjectDn.getName(), publicKey.getEncoded(),
                publicKey.getAlgorithm());
    }

    /**
     * Create a new trusted CA configuration, based on a trust anchor.
     * 
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     * 
     * @return The new trusted CA configuration.
     */
    public static TrustedCertificateAuthority createTrustedCA(final X500Principal subjectDn, final PublicKey publicKey) {
        return new TrustedCertificateAuthority()
                .setSubjectDn(subjectDn.getName())
                .setPublicKey(publicKey.getEncoded())
                .setNotBefore(Instant.now())
                .setNotAfter(Instant.now().plus(Period.ofDays(365)))
                .setKeyAlgorithm(publicKey.getAlgorithm());
    }

    /**
     * Create a new tenant, based on a trust anchor.
     * 
     * @param subjectDn  The subject DN of the trust anchor.
     * @param publicKey  The public key for the anchor.
     * @param algorithm  The algorithm for the public key.
     * 
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final String subjectDn, final byte[] publicKey,
            final String algorithm) {
        return createTenantForTrustAnchor(subjectDn, publicKey, algorithm, Instant.now(),
                Instant.now().plus(Period.ofDays(360)));
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param subjectDn The subject DN of the trust anchor.
     * @param publicKey The public key for the anchor.
     * @param notBefore The start date/time of the certificate's validity period.
     * @param notAfter  The end date/time of the certificate's validity period.
     * 
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final X500Principal subjectDn, final PublicKey publicKey,
            final Instant notBefore, final Instant notAfter) {
        return createTenantForTrustAnchor(subjectDn.getName(X500Principal.RFC2253), publicKey.getEncoded(),
                publicKey.getAlgorithm(), notBefore, notAfter);
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param subjectDn  The subject DN of the trust anchor.
     * @param publicKey  The public key for the anchor.
     * @param algorithm  The algorithm for the public key.
     * @param notBefore  The start date/time in which the certificate becomes valid.
     * @param notAfter   The end date/time in which the certificate becomes invalid.
     * 
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final String subjectDn, final byte[] publicKey, final String algorithm,
            final Instant notBefore, final Instant notAfter) {
        final var trustedCa = new TrustedCertificateAuthority()
                .setSubjectDn(subjectDn)
                .setPublicKey(publicKey)
                .setNotBefore(notBefore)
                .setNotAfter(notAfter)
                .setKeyAlgorithm(algorithm);

        final var trustedAuthorities = new ArrayList<TrustedCertificateAuthority>();
        trustedAuthorities.add(trustedCa);

        return new Tenant().setTrustedAuthorities(trustedAuthorities);
    }
}
