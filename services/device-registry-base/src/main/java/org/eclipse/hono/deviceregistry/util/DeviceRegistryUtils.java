/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.util;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A collection of utility methods for implementing device registries.
 *
 */
public final class DeviceRegistryUtils {

    private DeviceRegistryUtils() {
        // prevent instantiation
    }

    /**
     * Converts tenant object of type {@link Tenant} to an object of type {@link TenantObject}.
     *
     * @param tenantId The identifier of the tenant.
     * @param source   The source tenant object.
     * @return The converted tenant object of type {@link TenantObject}
     * @throws NullPointerException if the tenantId or source is null.
     */
    public static JsonObject convertTenant(final String tenantId, final Tenant source) {
        return convertTenant(tenantId, source, false);
    }

    /**
     * Converts tenant object of type {@link Tenant} to an object of type {@link TenantObject}.
     *
     * @param tenantId The identifier of the tenant.
     * @param source   The source tenant object.
     * @param filterAuthorities if set to true filter out CAs which are not valid at this point in time.
     * @return The converted tenant object of type {@link TenantObject}
     * @throws NullPointerException if the tenantId or source is null.
     */
    public static JsonObject convertTenant(final String tenantId, final Tenant source, final boolean filterAuthorities) {

        final Instant now = Instant.now();

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(source);

        final TenantObject target = TenantObject.from(tenantId, Optional.ofNullable(source.isEnabled()).orElse(true));
        target.setResourceLimits(source.getResourceLimits());
        target.setTracingConfig(source.getTracing());

        Optional.ofNullable(source.getMinimumMessageSize())
        .ifPresent(size -> target.setMinimumMessageSize(size));

        Optional.ofNullable(source.getDefaults())
        .map(JsonObject::new)
        .ifPresent(defaults -> target.setDefaults(defaults));

        Optional.ofNullable(source.getAdapters())
                .filter(adapters -> !adapters.isEmpty())
                .map(adapters -> adapters.stream()
                                .map(adapter -> JsonObject.mapFrom(adapter))
                                .map(json -> json.mapTo(org.eclipse.hono.util.Adapter.class))
                                .collect(Collectors.toList()))
                .ifPresent(adapters -> target.setAdapters(adapters));

        Optional.ofNullable(source.getExtensions())
        .map(JsonObject::new)
        .ifPresent(extensions -> target.setProperty(RegistryManagementConstants.FIELD_EXT, extensions));

        Optional.ofNullable(source.getTrustedCertificateAuthorities())
        .map(list -> list.stream()
                .filter(ca -> {
                    if (filterAuthorities) {
                        // filter out CAs which are not valid at this point in time
                        return !now.isBefore(ca.getNotBefore()) && !now.isAfter(ca.getNotAfter());
                    } else {
                        return true;
                    }
                })
                .map(ca -> JsonObject.mapFrom(ca))
                .map(json -> {
                    // validity period is not included in TenantObject
                    json.remove(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE);
                    json.remove(RegistryManagementConstants.FIELD_SECRETS_NOT_AFTER);
                    return json;
                })
                .collect(JsonArray::new, JsonArray::add, JsonArray::add))
        .ifPresent(authorities -> target.setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, authorities));

        return JsonObject.mapFrom(target);
    }

    /**
     * Gets the cache directive corresponding to the given max age for the cache.
     *
     * @param cacheMaxAge the maximum period of time in seconds that the information
     *                    returned by the service's operations may be cached for.
     * @return the cache directive corresponding to the given max age for the cache.
     */
    public static CacheDirective getCacheDirective(final int cacheMaxAge) {
        if (cacheMaxAge > 0) {
            return CacheDirective.maxAgeDirective(cacheMaxAge);
        } else {
            return CacheDirective.noCacheDirective();
        }
    }

    /**
     * Gets a unique identifier generated using {@link UUID#randomUUID()}.
     * 
     * @return The generated unique identifier.
     */
    public static String getUniqueIdentifier() {
        return UUID.randomUUID().toString();
    }
}
