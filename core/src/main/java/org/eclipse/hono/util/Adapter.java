/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Protocol Adapter configuration properties.
 * <p>
 * Represents the <em>Adapter</em> schema object defined in the
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class Adapter {

    @JsonProperty(RequestResponseApiConstants.FIELD_ENABLED)
    private boolean enabled = false;

    @JsonProperty(value = RegistryManagementConstants.FIELD_ADAPTERS_TYPE, required = true)
    private String type;

    @JsonProperty(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)
    private boolean deviceAuthenticationRequired = true;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    /**
     * Creates a new adapter instance for the given type.
     *
     * @param type The adapter type.
     * @throws NullPointerException if the type is {@code null}.
     */
    public Adapter(@JsonProperty(value = RegistryManagementConstants.FIELD_ADAPTERS_TYPE) final String type) {
        Objects.requireNonNull(type);
        this.type = type;
    }

    /**
     * Sets whether devices should be able to connect to Hono
     * using this protocol adapter.
     *
     * @param enabled {@code true} if devices should be able to connect.
     * @return This instance, to allow chained invocations.
     */
    public final Adapter setEnabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Checks whether devices are able to connect to Hono
     * using this protocol adapter.
     *
     * @return {@code true} if devices are able to connect.
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Gets the type name of the protocol adapter this is the
     * configuration for.
     *
     * @return The type name.
     */
    public final String getType() {
        return type;
    }

    /**
     * Sets whether devices are required to authenticate when connecting to
     * this protocol adapter.
     *
     * @param required {@code true} if devices should be required to authenticate.
     * @return A reference to this for fluent use.
     */
    public final Adapter setDeviceAuthenticationRequired(final boolean required) {
        this.deviceAuthenticationRequired = required;
        return this;
    }

    /**
     * Checks whether devices are required to authenticate when connecting to
     * this protocol adapter.
     *
     * @return {@code true} if devices are required to authenticate.
     */
    public final boolean isDeviceAuthenticationRequired() {
        return deviceAuthenticationRequired;
    }

    /**
     * Sets the extension properties for this adapter.
     * <p>
     * Existing extension properties are completely replaced by the new properties.
     *
     * @param extensions The extension properties.
     * @return This instance, to allow chained invocations.
     */
    public final Adapter setExtensions(final Map<String, Object> extensions) {
        this.extensions.clear();
        if (extensions != null) {
            this.extensions.putAll(extensions);
        }
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
     * Adds an extension property to this adapter.
     * <p>
     * If an extension property already exists for the specified key, the old value is replaced by the specified value.
     *
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allow chained invocations.
     * @throws NullPointerException if any of the arguments are {@code null}.
     */
    public final Adapter putExtension(final String key, final Object value) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        this.extensions.put(key, value);
        return this;
    }

}
