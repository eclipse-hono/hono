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
import java.util.LinkedList;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.tenant.Adapter;
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
        return createTenantForTrustAnchor(cert, cert.getPublicKey());
    }

    /**
     * Create a new tenant, based on a trust anchor.
     *
     * @param cert The trust anchor.
     * @param publicKey The public key for the anchor.
     * @return The new tenant. Never returns {@code null}.
     */
    public static Tenant createTenantForTrustAnchor(final X509Certificate cert, final PublicKey publicKey) {
        final var tenant = new Tenant();
        final var trustedCa = new TrustedCertificateAuthority();
        trustedCa.setSubjectDn(cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        trustedCa.setPublicKey(publicKey.getEncoded());
        trustedCa.setKeyAlgorithm(publicKey.getAlgorithm());
        tenant.setTrustedCertificateAuthority(trustedCa);
        return tenant;
    }

    /**
     * Create a new adapter section and set the enabled state.
     * 
     * @param tenant The tenant to process.
     * @param type The adapter type to configure.
     * @param state The enabled state.
     */
    public static void setAdapterEnabled(final Tenant tenant, final String type, final Boolean state) {
        final Adapter adapter = new Adapter();
        adapter.setType(type);
        adapter.setEnabled(state);
        if (tenant.getAdapters() == null) {
            tenant.setAdapters(new LinkedList<>());
        }
        tenant.getAdapters().add(adapter);

    }

}
