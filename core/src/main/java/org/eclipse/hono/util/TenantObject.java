/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * Encapsulates the tenant information that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>.
 */
public final class TenantObject {

    @JsonProperty(TenantConstants.FIELD_TENANT_ID)
    private String tenantId;
    @JsonProperty(TenantConstants.FIELD_ENABLED)
    private boolean enabled = true;
    private Map<String, JsonObject> adapterConfigurations;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the configuration information for this tenant's
     * configured adapters.
     * 
     * @return An unmodifiable list of configuration properties or
     *         {@code null} if no specific configuration has been
     *         set for any protocol adapter.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public List<Map<String, Object>> getAdapterConfigurationsAsMaps() {
        if (adapterConfigurations == null) {
            return null;
        } else {
            final List<Map<String, Object>> result = new LinkedList<>();
            adapterConfigurations.values().forEach(config -> result.add(((JsonObject) config).getMap()));
            return result;
        }
    }

    /**
     * Gets the configuration information for this tenant's
     * configured adapters.
     * 
     * @return The configuration properties for this tenant's
     *         configured adapters or {@code null} if no specific
     *         configuration has been set for any protocol adapter.
     */
    public JsonArray getAdapterConfigurations() {
        if (adapterConfigurations == null) {
            return null;
        } else {
            final JsonArray result = new JsonArray();
            adapterConfigurations.values().forEach(config -> result.add((JsonObject) config));
            return result;
        }
    }

    /**
     * Sets the configuration information for this tenant's
     * configured adapters.
     * 
     * @param configurations A list of configuration properties, one set of properties
     *                              for each configured adapter. The list's content will be
     *                              copied into a new list in order to prevent modification
     *                              of the list after this method has been invoked.
     * @throws NullPointerException if the list is {@code null}.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public void setAdapterConfigurations(final List<Map<String, Object>> configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            configurations.stream().forEach(config -> {
                addAdapterConfiguration(new JsonObject(config));
            });
        }
    }

    /**
     * Sets the configuration information for this tenant's
     * configured adapters.
     * 
     * @param configurations The configuration properties for this tenant's
     *                       configured adapters or {@code null} in order to
     *                       remove any existing configuration.
     */
    public void setAdapterConfigurations(final JsonArray configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            this.adapterConfigurations = new HashMap<>();
            configurations.stream().filter(obj -> JsonObject.class.isInstance(obj)).forEach(config -> {
                addAdapterConfiguration((JsonObject) config);
            });
        }
    }

    /**
     * Adds configuration information for a protocol adapter.
     * 
     * @param config The configuration properties to add.
     */
    public void addAdapterConfiguration(final JsonObject config) {
        final String type = config.getString(TenantConstants.FIELD_ADAPTERS_TYPE);
        if (type != null) {
            if (adapterConfigurations == null) {
                adapterConfigurations= new HashMap<>();
            }
            adapterConfigurations.put(type, config);
        }
    }

    /**
     * Checks if a given protocol adapter is enabled for this tenant.
     * 
     * @param typeName The type name of the adapter.
     * @return {@code true} if this tenant and the given adapter are enabled.
     */
    public boolean isAdapterEnabled(final String typeName) {

        if (!enabled) {
            return false;
        } else if (adapterConfigurations == null) {
            // all adapters are enabled
            return true;
        } else {
            final JsonObject config = adapterConfigurations.get(typeName);
            if (config == null) {
                // if not explicitly configured, the adapter is disabled by default
                return false;
            } else {
                return config.getBoolean(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
            }
        }
    }

    /**
     * Creates a TenantObject for a tenantId and the enabled property.
     *
     * @param tenantId The tenant for which the object is constructed.
     * @param enabled {@code true} if the tenant shall be enabled.
     * @return The TenantObject.
     * @throws NullPointerException if any of tenantId or enabled is {@code null}.
     */
    public static TenantObject from(final String tenantId, final Boolean enabled) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(enabled);

        final TenantObject result = new TenantObject();
        result.setTenantId(tenantId);
        result.setEnabled(enabled);
        return result;
    }
}
