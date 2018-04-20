/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Dejan Bosanac - initial implementation
 *******************************************************************************/
package org.eclipse.hono.util;

/**
 * Utility used to determine type of the endpoint.
 */
public enum EndpointType {
    TELEMETRY, EVENT, UNKNOWN;

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
        default:
            return UNKNOWN;
        }
    }
}
