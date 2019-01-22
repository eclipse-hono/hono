/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

/**
 * Utility used to determine type of the endpoint.
 */
public enum EndpointType {

    /**
     * The endpoint for telemetry messages.
     */
    TELEMETRY(TelemetryConstants.TELEMETRY_ENDPOINT),
    /**
     * The endpoint for events.
     */
    EVENT(EventConstants.EVENT_ENDPOINT),
    /**
     * The endpoint for command &amp; control messages.
     */
    CONTROL(CommandConstants.COMMAND_ENDPOINT),
    /**
     * The unknown endpoint.
     */
    UNKNOWN("unknown");

    private final String canonicalName;

    EndpointType(final String canonicalName) {
        this.canonicalName = canonicalName;
    }

    /**
     * Gets this type's canonical name.
     * 
     * @return The name.
     */
    public String getCanonicalName() {
        return canonicalName;
    }

    /**
     * Gets the endpoint type from a string value.
     * 
     * @param name The name of the endpoint type.
     * 
     * @return The enum literal of the endpoint type. Returns {@link #UNKNOWN} if it cannot find the endpoint type.
     *         Never returns {@code null}.
     */
    public static EndpointType fromString(final String name) {
        switch (name) {
        case TelemetryConstants.TELEMETRY_ENDPOINT:
        case TelemetryConstants.TELEMETRY_ENDPOINT_SHORT:
            return TELEMETRY;
        case EventConstants.EVENT_ENDPOINT:
        case EventConstants.EVENT_ENDPOINT_SHORT:
            return EVENT;
        case CommandConstants.COMMAND_ENDPOINT:
        case CommandConstants.COMMAND_ENDPOINT_SHORT:
            return CONTROL;
        default:
            return UNKNOWN;
        }
    }
}
