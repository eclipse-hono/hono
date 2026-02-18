/**
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.adapter;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.TenantObject;

/**
 * Helper methods for resolving client IP configuration.
 */
public final class ClientIpConfigHelper {

    private ClientIpConfigHelper() {
        // utility class
    }

    /**
     * Determines whether the client IP property should be included.
     *
     * @param tenant The tenant configuration.
     * @param adapterType The adapter type name.
     * @param config The adapter configuration.
     * @return {@code true} if client IP should be included.
     */
    public static boolean isClientIpEnabled(
            final TenantObject tenant,
            final String adapterType,
            final ProtocolAdapterProperties config) {
        Objects.requireNonNull(adapterType);
        Objects.requireNonNull(config);
        return Optional.ofNullable(tenant)
                .map(currentTenant -> currentTenant.getAdapter(adapterType))
                .map(Adapter::isClientIpEnabled)
                .orElse(config.isClientIpEnabled());
    }

    /**
     * Determines the client IP source to use.
     *
     * @param tenant The tenant configuration.
     * @param adapterType The adapter type name.
     * @param config The adapter configuration.
     * @return The client IP source.
     */
    public static ClientIpSource getClientIpSource(
            final TenantObject tenant,
            final String adapterType,
            final ProtocolAdapterProperties config) {
        Objects.requireNonNull(adapterType);
        Objects.requireNonNull(config);
        return Optional.ofNullable(tenant)
                .map(currentTenant -> currentTenant.getAdapter(adapterType))
                .map(Adapter::getClientIpSource)
                .orElse(config.getClientIpSource());
    }
}
