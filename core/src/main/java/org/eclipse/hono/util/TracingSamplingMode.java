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

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value that defines in how far <em>OpenTracing</em> spans shall be recorded (sampled) by the tracing system.
 */
public enum TracingSamplingMode {
    DEFAULT("default"),
    ALL("all"),
    NONE("none"),
    ;

    private final String fieldValue;

    TracingSamplingMode(final String fieldValue) {
        this.fieldValue = fieldValue;
    }

    /**
     * Gets the JSON field value for this TraceSamplingMode.
     *
     * @return The field value.
     */
    @JsonValue
    public String getFieldValue() {
        return fieldValue;
    }

    /**
     * Construct a TraceSamplingMode from a value.
     *
     * @param value The value from which the TraceSamplingMode needs to be constructed.
     * @return The TraceSamplingMode as enum, or {@link TracingSamplingMode#DEFAULT} otherwise.
     */
    public static TracingSamplingMode from(final String value) {
        for (TracingSamplingMode mode : values()) {
            if (mode.getFieldValue().equals(value)) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
