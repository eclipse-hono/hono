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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.json.JsonObject;

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
    public static boolean isClientIpIncluded(
            final TenantObject tenant,
            final String adapterType,
            final ProtocolAdapterProperties config) {
        Objects.requireNonNull(adapterType);
        Objects.requireNonNull(config);
        return getBooleanExtensionValue(tenant, adapterType, TenantConstants.FIELD_EXT_INCLUDE_CLIENT_IP)
                .orElse(config.isClientIpIncluded());
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
        return getStringExtensionValue(tenant, adapterType, TenantConstants.FIELD_EXT_CLIENT_IP_SOURCE)
                .flatMap(ClientIpSource::fromString)
                .orElse(config.getClientIpSource());
    }

    private static Optional<Boolean> getBooleanExtensionValue(
            final TenantObject tenant,
            final String adapterType,
            final String key) {
        return getExtensionValue(tenant, adapterType, key)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast);
    }

    private static Optional<String> getStringExtensionValue(
            final TenantObject tenant,
            final String adapterType,
            final String key) {
        return getExtensionValue(tenant, adapterType, key)
                .filter(String.class::isInstance)
                .map(String.class::cast);
    }

    private static Optional<Object> getExtensionValue(
            final TenantObject tenant,
            final String adapterType,
            final String key) {
        if (tenant == null || key == null) {
            return Optional.empty();
        }
        return getAdapterExtensionValue(tenant, adapterType, key)
                .or(() -> getTenantExtensionValue(tenant, key));
    }

    private static Optional<Object> getTenantExtensionValue(final TenantObject tenant, final String key) {
        return Optional.ofNullable(tenant.getProperty(TenantConstants.FIELD_EXT, JsonObject.class))
                .map(extensions -> extensions.getValue(key));
    }

    private static Optional<Object> getAdapterExtensionValue(
            final TenantObject tenant,
            final String adapterType,
            final String key) {
        return Optional.ofNullable(tenant.getAdapter(adapterType))
                .map(Adapter::getExtensions)
                .map(Map::entrySet)
                .flatMap(entries -> entries.stream()
                        .filter(entry -> key.equals(entry.getKey()))
                        .findFirst()
                        .map(Map.Entry::getValue));
    }
}
