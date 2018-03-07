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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Encapsulates the tenant information that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>.
 */
public final class TenantObject {

    @JsonProperty(TenantConstants.FIELD_TENANT_ID)
    private String tenantId;
    @JsonProperty(TenantConstants.FIELD_ENABLED)
    private Boolean enabled;
    /*
     * Since the format of the adapters field is not determined by the Tenant API, they are best represented as
     * key-value maps with key and value both of type String.
     * The further processing of adapterConfigurations is part of the validator for the specific type.
     */
    @JsonProperty(TenantConstants.FIELD_ADAPTERS)
    private List<Map<String, String>> adapterConfigurations;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId=tenantId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public List<Map<String, String>> getAdapterConfigurations() {
        return adapterConfigurations != null ? Collections.unmodifiableList(adapterConfigurations) : null;
    }

    public void setAdapterConfigurations(final List<Map<String, String>> adapterConfigurations) {
        this.adapterConfigurations= new LinkedList<>(adapterConfigurations);
    }

    public void addAdapterConfiguration(final Map<String, String> adapterConfiguration) {
        if (adapterConfigurations == null) {
            adapterConfigurations= new LinkedList<>();
        }
        adapterConfigurations.add(adapterConfiguration);
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
