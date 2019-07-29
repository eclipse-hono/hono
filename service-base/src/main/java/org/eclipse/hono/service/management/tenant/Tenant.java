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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.eclipse.hono.util.RegistryManagementConstants;

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

    @JsonProperty(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA)
    private TrustedCertificateAuthority trustedCertificateAuthority;

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    public void setDefaults(final Map<String, Object> defaults) {
        this.defaults = defaults;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public List<Adapter> getAdapters() {
        return adapters;
    }

    public void setAdapters(final List<Adapter> adapters) {
        this.adapters = adapters;
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
     * @throws IllegalArgumentException if the minimum message size is negative.
     */
    public void setMinimumMessageSize(final Integer minimumMessageSize) {
        if (minimumMessageSize == null || minimumMessageSize < 0) {
            throw new IllegalArgumentException("minimum message size must be >= 0");
        }
        this.minimumMessageSize = minimumMessageSize;
    }

    public ResourceLimits getResourceLimits() {
        return resourceLimits;
    }

    public void setResourceLimits(final ResourceLimits limits) {
        this.resourceLimits = limits;
    }

    public TrustedCertificateAuthority getTrustedCertificateAuthority() {
        return trustedCertificateAuthority;
    }

    public void setTrustedCertificateAuthority(final TrustedCertificateAuthority trustedCertificateAuthority) {
        this.trustedCertificateAuthority = trustedCertificateAuthority;
    }

    /**
     * Add a new extension entry to the Tenant.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allowed chained invocations.
     */
    public Tenant putExtension(final String key, final Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }
}
