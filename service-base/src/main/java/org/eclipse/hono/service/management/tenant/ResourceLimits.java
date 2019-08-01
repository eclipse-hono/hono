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
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Resource limits definition.
 */
public class ResourceLimits {

    @JsonProperty(RegistryManagementConstants.FIELD_RESOURCE_LIMITS_MAX_CONNECTIONS)
    private int maxConnections = -1;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Object> extensions;

    /**
     * Set the max connections property for this resource limits.
     * 
     * @param maxConnections  The maximum connections to set.
     * @return  a reference to this for fluent use.
     */
    public ResourceLimits setMaxConnections(final Integer maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public Integer getMaxConnections() {
        return this.maxConnections;
    }

    /**
     * Set the extension properties for this resource limits.
     * 
     * @param extensions The extensions to set.
     * @return          a reference to this for fluent use.
     */
    public ResourceLimits setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    /**
     * Adds an extension property to this resource limit.
     * <p>
     * If an extension property already exist for the specified key, the old value is replaced by the specified value.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allow chained invocations.
     * @throws NullPointerException if any of the arguments is {@code null}.
     */
    public ResourceLimits putExtension(final String key, final Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

}
