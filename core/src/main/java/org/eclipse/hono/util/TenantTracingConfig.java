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

package org.eclipse.hono.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A tenant specific tracing configuration.
 */
public class TenantTracingConfig {

    @JsonProperty(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE)
    private TracingSamplingMode samplingMode;

    @JsonProperty(RegistryManagementConstants.FIELD_TRACING_SAMPLING_MODE_PER_AUTH_ID)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, TracingSamplingMode> samplingModePerAuthId = new HashMap<>();

    /**
     * Gets the sampling mode that defines in how far spans created when processing
     * messages for a tenant shall be recorded (sampled) by the tracing system.
     *
     * @return The sampling mode or {@code null} if not set.
     */
    public final TracingSamplingMode getSamplingMode() {
        return samplingMode;
    }

    /**
     * Sets the sampling mode that defines in how far spans created when processing
     * messages for a tenant shall be recorded (sampled) by the tracing system.
     *
     * @param samplingMode The sampling mode.
     * @return This instance for command chaining.
     */
    public final TenantTracingConfig setSamplingMode(final TracingSamplingMode samplingMode) {
        this.samplingMode = samplingMode;
        return this;
    }

    /**
     * Gets the sampling modes defined for specific devices.
     * <p>
     * The sampling mode for a specific device overrides the value returned by {@link #getSamplingMode()}.
     *
     * @return An unmodifiable view on the device specific sampling modes.
     *         The returned map contains authentication identifiers as keys.
     */
    public Map<String, TracingSamplingMode> getSamplingModePerAuthId() {
        return Collections.unmodifiableMap(samplingModePerAuthId);
    }

    /**
     * Sets the sampling modes defined for specific devices.
     * <p>
     * The sampling mode for a specific device overrides the value returned by {@link #getSamplingMode()}.
     *
     * @param samplingModePerAuthId The device specific sampling modes.
     *                              The map must contain authentication identifiers as keys.
     * @return This instance for command chaining.
     */
    public TenantTracingConfig setSamplingModePerAuthId(final Map<String, TracingSamplingMode> samplingModePerAuthId) {
        this.samplingModePerAuthId.clear();
        if (samplingModePerAuthId != null) {
            this.samplingModePerAuthId.putAll(samplingModePerAuthId);
        }
        return this;
    }

    /**
     * Gets the sampling mode for a specific device.
     *
     * @param authId The authentication identity of the device.
     * @return The sampling mode set for the device or
     *         the default sampling mode if no mode has been set for the device or
     *         {@code null} if no default sampling mode has been set.
     */
    @JsonIgnore
    public final TracingSamplingMode getSamplingMode(final String authId) {
        if (authId == null) {
            return samplingMode;
        }
        return Optional.ofNullable(samplingModePerAuthId.get(authId))
                .orElse(samplingMode);
    }
}
