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

import java.util.HashMap;
import java.util.Map;

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
    public TracingSamplingMode getSamplingMode() {
        return samplingMode;
    }

    /**
     * Sets the sampling mode that defines in how far spans created when processing
     * messages for a tenant shall be recorded (sampled) by the tracing system.
     *
     * @param samplingMode The sampling mode.
     */
    public void setSamplingMode(final TracingSamplingMode samplingMode) {
        this.samplingMode = samplingMode;
    }

    /**
     * Gets the Map that contains the sampling mode defined per auth-id.
     * <p>
     * The sampling mode for a specific auth-id overrides the value returned by {@link #getSamplingMode()}.
     *
     * @return Map with auth-id as key and sampling mode as value.
     */
    public Map<String, TracingSamplingMode> getSamplingModePerAuthId() {
        return samplingModePerAuthId;
    }

    /**
     * Sets the Map that contains the sampling mode defined per auth-id.
     * <p>
     * The sampling mode for a specific auth-id overrides the value returned by {@link #getSamplingMode()}.
     *
     * @param samplingModePerAuthId Map with auth-id as key and sampling mode as value.
     */
    public void setSamplingModePerAuthId(final Map<String, TracingSamplingMode> samplingModePerAuthId) {
        this.samplingModePerAuthId = samplingModePerAuthId;
    }
}
