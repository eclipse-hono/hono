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
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


/**
 * Encapsulates the tenant information that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>.
 */
@JsonInclude(value = Include.NON_NULL)
public final class TenantObject {

    @JsonIgnore
    private Map<String, JsonObject> adapterConfigurations;

    @JsonIgnore
    private final JsonObject json = new JsonObject();

    /**
     * Gets a map of this tenant's properties that should be included
     * in serialization to JSON in addition to the explicitly annotated
     * properties.
     * 
     * @return The properties.
     */
    @JsonAnyGetter
    private Map<String, Object> getPropertiesAsMap() {
        return json.getMap();
    }

    /**
     * Gets a property value.
     * 
     * @param name The property name.
     * @param <T> The type of the property.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if name is {@code null}.
     */
    public <T> T getProperty(final String name) {
        return getProperty(json, name);
    }

    @SuppressWarnings("unchecked")
    private <T> T getProperty(final JsonObject parent, final String name) {
        final Object value = parent.getValue(Objects.requireNonNull(name));
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Adds a property to this tenant.
     * 
     * @param name The property name.
     * @param value The property value.
     * @return This tenant for command chaining.
     * @throws NullPointerException if name is {@code null}.
     */
    @JsonAnySetter
    public TenantObject setProperty(final String name, final Object value) {
        json.put(Objects.requireNonNull(name), value);
        return this;
    }

    /**
     * Gets this tenant's identifier.
     * 
     * @return The identifier or {@code null} if not set.
     */
    @JsonIgnore
    public String getTenantId() {
        return json.getString(TenantConstants.FIELD_PAYLOAD_TENANT_ID);
    }

    /**
     * Sets this tenant's identifier.
     * 
     * @param tenantId The identifier.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setTenantId(final String tenantId) {
        json.put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        return this;
    }

    /**
     * Checks if this tenant is enabled.
     * 
     * @return {@code true} if this tenant is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return json.getBoolean(TenantConstants.FIELD_ENABLED, true);
    }

    /**
     * Sets whether this tenant is enabled.
     * 
     * @param flag {@code true} if this tenant is enabled.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setEnabled(final boolean flag) {
        json.put(TenantConstants.FIELD_ENABLED, flag);
        return this;
    }

    /**
     * Gets the subject DN of this tenant's configured trusted
     * certificate authority.
     * 
     * @return The DN or {@code null} if no CA has been set.
     */
    @JsonIgnore
    public X500Principal getTrustedCaSubjectDn() {

        final JsonObject trustedCa = getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
        if (trustedCa == null) {
            return null;
        } else {
            return Optional.ofNullable((String) getProperty(trustedCa, TenantConstants.FIELD_PAYLOAD_SUBJECT_DN))
                    .map(dn -> new X500Principal(dn)).orElse(null);
        }
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
    @JsonIgnore
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
     * Gets the configuration properties for a protocol adapter.
     * 
     * @param type The adapter's type.
     * @return The configuration properties or {@code null} if no specific
     *         properties have been set.
     */
    public JsonObject getAdapterConfiguration(final String type) {
        if (adapterConfigurations == null) {
            return null;
        } else {
            return adapterConfigurations.get(type);
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
     * @return This tenant for command chaining.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    public TenantObject setAdapterConfigurations(final List<Map<String, Object>> configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            configurations.stream().forEach(map -> {
                final JsonObject config = new JsonObject(map);
                addAdapterConfiguration(config);
            });
        }
        return this;
    }

    /**
     * Sets the configuration information for this tenant's
     * configured adapters.
     * 
     * @param configurations The configuration properties for this tenant's
     *                       configured adapters or {@code null} in order to
     *                       remove any existing configuration.
     * @return This tenant for command chaining.
     */
    @JsonIgnore
    public TenantObject setAdapterConfigurations(final JsonArray configurations) {
        if (configurations == null) {
            this.adapterConfigurations = null;
        } else {
            this.adapterConfigurations = new HashMap<>();
            configurations.stream().filter(obj -> JsonObject.class.isInstance(obj)).forEach(config -> {
                addAdapterConfiguration((JsonObject) config);
            });
        }
        return this;
    }

    /**
     * Adds configuration information for a protocol adapter.
     * 
     * @param config The configuration properties to add.
     * @throws NullPointerException if config is {@code null}.
     * @throws IllegalArgumentException if the given configuration does not contain
     *                a <em>type</em> name.
     * @return This tenant for command chaining.
     */
    public TenantObject addAdapterConfiguration(final JsonObject config) {

        final Object type = config.getValue(TenantConstants.FIELD_ADAPTERS_TYPE);
        if (String.class.isInstance(type)) {
            if (adapterConfigurations == null) {
                adapterConfigurations= new HashMap<>();
            }
            adapterConfigurations.put((String) type, config);
        } else {
            throw new IllegalArgumentException("adapter configuration must contain type field");
        }
        return this;
    }

    /**
     * Checks if a given protocol adapter is enabled for this tenant.
     * 
     * @param typeName The type name of the adapter.
     * @return {@code true} if this tenant and the given adapter are enabled.
     */
    @JsonIgnore
    public boolean isAdapterEnabled(final String typeName) {

        if (!isEnabled()) {
            return false;
        } else if (adapterConfigurations == null) {
            // all adapters are enabled
            return true;
        } else {
            final JsonObject config = getAdapterConfiguration(typeName);
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

    /**
     * Creates new protocol adapter configuration properties.
     * 
     * @param type The adapter type.
     * @param enabled {@code true} if the adapter should be enabled.
     * @return The configuration properties.
     */
    public static JsonObject newAdapterConfig(final String type, final boolean enabled) {

        Objects.requireNonNull(type);
        return new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, type)
                .put(TenantConstants.FIELD_ENABLED, enabled);
    }
}
