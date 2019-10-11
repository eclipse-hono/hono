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

package org.eclipse.hono.service.management.tenant;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantTracingConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about a Hono Tenant.
 * <p>
 * Represents the <em>Tenant</em> schema object defined in the
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_NULL)
public class Tenant {

    @JsonProperty(RegistryManagementConstants.FIELD_ENABLED)
    private Boolean enabled;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_DEFAULTS)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Object> defaults = new HashMap<>();

    @JsonProperty(RegistryManagementConstants.FIELD_ADAPTERS)
    @JsonInclude(Include.NON_EMPTY)
    private List<Adapter> adapters = new LinkedList<>();

    @JsonProperty(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE)
    @JsonInclude(Include.NON_DEFAULT)
    private int minimumMessageSize = RegistryManagementConstants.DEFAULT_MINIMUM_MESSAGE_SIZE;

    @JsonProperty(RegistryManagementConstants.FIELD_RESOURCE_LIMITS)
    @JsonInclude(Include.NON_DEFAULT)
    private ResourceLimits resourceLimits;

    @JsonProperty(RegistryManagementConstants.FIELD_TRACING)
    @JsonInclude(Include.NON_EMPTY)
    private TenantTracingConfig tracing;

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA)
    private TrustedCertificateAuthority trustedCertificateAuthority;

    /**
     * Sets whether devices of this tenant should be able to connect
     * to Hono.
     * 
     * @param enabled {@code true} if devices should be able to connect.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Checks whether devices of this tenant are able to connect
     * to Hono.
     * 
     * @return {@code true} if devices are able to connect.
     */
    public final Boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the extension properties for this tenant.
     * <p>
     * Existing extension properties are completely replaced by the new properties.
     * 
     * @param extensions The extension properties.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setExtensions(final Map<String, Object> extensions) {
        this.extensions.clear();
        if (extensions != null) {
            this.extensions.putAll(extensions);
        }
        return this;
    }

    /**
     * Adds an extension property to this tenant.
     * <p>
     * If an extension property already exists for the specified key, the old value is replaced by the new value.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allow chained invocations.
     * @throws NullPointerException if any of the arguments are {@code null}.
     */
    public final Tenant putExtension(final String key, final Object value) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.extensions.put(key, value);
        return this;
    }

    /**
     * Gets the extension properties of this tenant.
     * 
     * @return An unmodifiable view on the extension properties.
     */
    public final Map<String, Object> getExtensions() {
        return Collections.unmodifiableMap(this.extensions);
    }

    /**
     * Sets the default properties to use for devices belonging to this tenant.
     * <p>
     * Existing default properties are completely replaced by the new properties.
     * 
     * @param defaults The default properties.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setDefaults(final Map<String, Object> defaults) {
        this.defaults.clear();
        if (defaults != null) {
            this.defaults.putAll(defaults);
        }
        return this;
    }

    /**
     * Gets the default properties used for devices belonging to this tenant.
     * 
     * @return An unmodifiable view on the default properties.
     */
    public final Map<String, Object> getDefaults() {
        return Collections.unmodifiableMap(defaults);
    }

    /**
     * Sets protocol adapter configuration specific to this tenant.
     * <p>
     * Existing configuration properties are completely replaced by the new properties.
     *
     * @param adapters The configuration properties.
     * @return This instance, to allow chained invocations.
     * @throws IllegalArgumentException if the adapters list is empty.
     */
    public final Tenant setAdapters(final List<Adapter> adapters) {

        if (adapters != null) {
            if (adapters.isEmpty()) {
                throw new IllegalArgumentException("Atleast one adapter must be configured");
            }

            final Set<String> uniqueAdapterTypes = adapters.stream()
                    .map(Adapter::getType)
                    .collect(Collectors.toSet());
            if (adapters.size() != uniqueAdapterTypes.size()) {
                throw new IllegalArgumentException("Each adapter must have a unique type");
            }
        }

        this.adapters.clear();
        if (adapters != null) {
            this.adapters.addAll(adapters);
        }
        return this;
    }

    /**
     * Gets protocol adapter configuration specific to this tenant.
     *
     * @return An unmodifiable view on the adapter configuration properties.
     */
    public final List<Adapter> getAdapters() {
        return Collections.unmodifiableList(adapters);
    }

    /**
     * Adds protocol adapter configuration properties specific to this Tenant.
     * 
     * @param configuration The configuration properties to add.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant addAdapterConfig(final Adapter configuration) {

        if (configuration == null) {
            return this;
        }

        final boolean hasAdapterOfSameType = adapters.stream()
                .anyMatch(adapter -> configuration.getType().equals(adapter.getType()));
        if (hasAdapterOfSameType) {
            throw new IllegalArgumentException(
                    String.format("Already an adapter of the type [%s] exists", configuration.getType()));
        }
        adapters.add(configuration);
        return this;
    }

    /**
     * Gets the minimum message size in bytes.
     *
     * @return The minimum message size in bytes or 
     *         {@link RegistryManagementConstants#DEFAULT_MINIMUM_MESSAGE_SIZE} if not set.
     */
    public final Integer getMinimumMessageSize() {
        return minimumMessageSize;
    }

    /**
     * Sets the minimum message size in bytes.
     *
     * @param minimumMessageSize The minimum message size.
     *
     * @return This instance, to allow chained invocations.
     * @throws IllegalArgumentException if the minimum message size is negative.
     */
    public final Tenant setMinimumMessageSize(final Integer minimumMessageSize) {

        if (minimumMessageSize == null || minimumMessageSize < 0) {
            throw new IllegalArgumentException("minimum message size must be >= 0");
        }
        this.minimumMessageSize = minimumMessageSize;
        return this;
    }

    /**
     * Gets resource limits defined for this tenant.
     * 
     * @return The resource limits or {@code null} if not set.
     */
    public final ResourceLimits getResourceLimits() {
        return resourceLimits;
    }

    /**
     * Sets the resource limits for this tenant.
     * 
     * @param resourceLimits The resource limits to set.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setResourceLimits(final ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
        return this;
    }

    /**
     * Gets this tenant's tracing configuration.
     *
     * @return The tracing configuration or {@code null} if not set.
     */
    public final TenantTracingConfig getTracing() {
        return tracing;
    }

    /**
     * Sets this tenant's tracing configuration.
     *
     * @param tracing The tracing configuration.
     */
    public final void setTracing(final TenantTracingConfig tracing) {
        this.tracing = tracing;
    }

    /**
     * Gets the trusted certificate authority used for authenticating devices of this tenant.
     * 
     * @return The certificate authority or {@code null} if not set.
     */
    public final TrustedCertificateAuthority getTrustedCertificateAuthority() {
        return trustedCertificateAuthority;
    }

    /**
     * Sets the trusted certificate authority to use for authenticating devices of this tenant.
     * 
     * @param trustedCertificateAuthority The certificate authority.
     * @return This instance, to allow chained invocations.
     */
    public final Tenant setTrustedCertificateAuthority(final TrustedCertificateAuthority trustedCertificateAuthority) {
        this.trustedCertificateAuthority = trustedCertificateAuthority;
        return this;
    }
}
