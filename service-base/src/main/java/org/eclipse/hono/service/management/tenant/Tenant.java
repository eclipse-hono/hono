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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantTracingConfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tenant Information.
 */
@JsonInclude(value = Include.NON_NULL)
public class Tenant {

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
    @JsonInclude(Include.NON_EMPTY)
    private List<TrustedCertificateAuthority> trustedAuthorities = new LinkedList<>();

    /**
     * Sets the enabled property.
     * 
     * @param enabled The enabled property.
     * @return This instance, to allow chained invocations.
     */
    public Tenant setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Sets the extension properties for this tenant.
     * 
     * @param extensions The extension properties.
     * @return   This instance, to allow chained invocations.
     */
    public Tenant setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    /**
     * Sets the defaults properties for this tenant.
     * 
     * @param defaults  The defaults properties.
     * @return          This instance, to allow chained invocations.
     */
    public Tenant setDefaults(final Map<String, Object> defaults) {
        this.defaults = defaults;
        return this;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public List<Adapter> getAdapters() {
        return adapters;
    }

    /**
     * Sets the list of adapters for this tenant.
     *
     * @param adapters The adapters to set for the tenant.
     * @return   This instance, to allow chained invocations.
     */
    public Tenant setAdapters(final List<Adapter> adapters) {
        this.adapters = adapters;
        return this;
    }

    /**
     * Gets the minimum message size in bytes.
     *
     * @return The minimum message size in bytes or 
     *         {@link RegistryManagementConstants#DEFAULT_MINIMUM_MESSAGE_SIZE} if not set.
     */
    public Integer getMinimumMessageSize() {
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
    public Tenant setMinimumMessageSize(final Integer minimumMessageSize) {
        if (minimumMessageSize == null || minimumMessageSize < 0) {
            throw new IllegalArgumentException("minimum message size must be >= 0");
        }
        this.minimumMessageSize = minimumMessageSize;
        return this;
    }

    public ResourceLimits getResourceLimits() {
        return resourceLimits;
    }

    /**
     * Sets the resource limits for this tenant.
     * 
     * @param resourceLimits The resource limits to set.
     * @return  This instance, to allow chained invocations.
     */
    public Tenant setResourceLimits(final ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
        return this;
    }

    /**
     * Gets the tenant-specific tracing configuration.
     *
     * @return The tracing configuration or {@code null} if not set.
     */
    public TenantTracingConfig getTracing() {
        return tracing;
    }

    /**
     * Sets the tenant-specific tracing configuration.
     *
     * @param tracing The tracing configuration.
     */
    public void setTracing(final TenantTracingConfig tracing) {
        this.tracing = tracing;
    }

    public List<TrustedCertificateAuthority> getTrustedAuthorities() {
        return trustedAuthorities;
    }

    /**
     * Sets the trust configurations for this tenant.
     * 
     * @param trustedCertificateAuthority  The trust configurations to set.
     * @return  This instance, to allow chained invocations.
     */
    public Tenant setTrustedAuthorities(final List<TrustedCertificateAuthority> trustedAuthorities) {
        this.trustedAuthorities = trustedAuthorities;
        return this;
    }

    /**
     * Adds an extension property to this tenant.
     * <p>
     * If an extension property already exist for the specified key, the old value is replaced by the specified value.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allow chained invocations.
     * @throws NullPointerException if any of the arguments is {@code null}.
     */
    public Tenant putExtension(final String key, final Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    /**
     * Adds the specified adapter to the list of adapters for this Tenant.
     * 
     * @param adapter The adapter to add for the tenant.
     * @return        This instance, to allow chained invocations.
     * @throws        NullPointerException if the specified adapter is {@code null}.
     */
    public Tenant addAdapterConfig(final Adapter adapter) {

        Objects.requireNonNull(adapter);

        if (adapters == null) {
            adapters = new LinkedList<>();
        }
        adapters.add(adapter);
        return this;
    }

}
