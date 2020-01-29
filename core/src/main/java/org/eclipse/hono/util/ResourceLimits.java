/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resource limits definition.
 */
@JsonInclude(Include.NON_DEFAULT)
public class ResourceLimits {

    @JsonProperty(TenantConstants.FIELD_MAX_CONNECTIONS)
    private int maxConnections = TenantConstants.UNLIMITED_CONNECTIONS;
    @JsonProperty(TenantConstants.FIELD_MAX_TTL)
    private long maxTtl = TenantConstants.UNLIMITED_TTL;

    @JsonProperty(TenantConstants.FIELD_DATA_VOLUME)
    private DataVolume dataVolume;

    @JsonProperty(TenantConstants.FIELD_CONNECTION_DURATION)
    private ConnectionDuration connectionDuration;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Object> extensions;

    /**
     * Sets the maximum number of connected devices a tenant supports.
     * 
     * @param maxConnections The maximum number of connections to set.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the maximum connections is set to less than -1.
     */
    public final ResourceLimits setMaxConnections(final int maxConnections) {
        if (maxConnections < -1) {
            throw new IllegalArgumentException("Maximum connections property must be set to value >= -1");
        }
        this.maxConnections = maxConnections;
        return this;
    }

    /**
     * Gets the maximum number of connected devices a tenant supports.
     * 
     * @return The maximum number of connections or {@link TenantConstants#UNLIMITED_CONNECTIONS}
     *         if not set.
     */
    public final int getMaxConnections() {
        return this.maxConnections;
    }

    /**
     * Sets the maximum time-to-live to use for events published by
     * devices of a tenant.
     * 
     * @param maxTtl The time-to-live in seconds.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the time-to-live is set to less than -1.
     */
    public ResourceLimits setMaxTtl(final long maxTtl) {
        if (maxTtl < -1) {
            throw new IllegalArgumentException("Maximum time-to-live property must be set to value >= -1");
        }
        this.maxTtl = maxTtl;
        return this;
    }

    /**
     * Gets the maximum time-to-live to use for events published by
     * devices of a tenant.
     * 
     * @return The time-to-live in seconds.
     */
    public long getMaxTtl() {
        return this.maxTtl;
    }

    /**
     * Gets the data volume properties which are required for the message limit verification.
     *
     * @return The data volume properties.
     */
    public final DataVolume getDataVolume() {
        return dataVolume;
    }

    /**
     * Sets the data volume properties which are required for the message limit verification.
     * 
     * @param dataVolume the data volume properties.
     * @return a reference to this for fluent use.
     */
    public final ResourceLimits setDataVolume(final DataVolume dataVolume) {
        this.dataVolume = dataVolume;
        return this;
    }

    /**
     * Gets the properties that are required for the connection duration verification.
     *
     * @return The connection duration properties.
     */
    public final ConnectionDuration getConnectionDuration() {
        return connectionDuration;
    }

    /**
     * Sets the properties that are required for the connection duration verification.
     *
     * @param connectionDuration the connection duration properties.
     * @return a reference to this for fluent use.
     */
    public final ResourceLimits setConnectionDuration(final ConnectionDuration connectionDuration) {
        this.connectionDuration = connectionDuration;
        return this;
    }

    /**
     * Sets the extension properties for this resource limits.
     * 
     * @param extensions The extensions to set.
     * @return          a reference to this for fluent use.
     */
    public final ResourceLimits setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Gets the extension properties for this resource limits.
     * 
     * @return The extensions.
     */
    public final Map<String, Object> getExtensions() {
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
    public final ResourceLimits putExtension(final String key, final Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

}
