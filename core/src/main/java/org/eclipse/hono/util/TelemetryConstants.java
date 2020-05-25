/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
 * Constants &amp; utility methods used throughout the Telemetry API.
 */
public final class TelemetryConstants {

    /**
     * The name of the Telemetry API endpoint.
     */
    public static final String TELEMETRY_ENDPOINT = "telemetry";

    /**
     * The short name of the Telemetry API endpoint.
     */
    public static final String TELEMETRY_ENDPOINT_SHORT = "t";

    private TelemetryConstants() {
    }

    /**
     * Checks if a given endpoint name is the Telemetry endpoint.
     *
     * @param ep The name to check.
     * @return {@code true} if the name is either {@link #TELEMETRY_ENDPOINT} or {@link #TELEMETRY_ENDPOINT_SHORT}.
     */
    public static boolean isTelemetryEndpoint(final String ep) {
        return TELEMETRY_ENDPOINT.equals(ep) || TELEMETRY_ENDPOINT_SHORT.equals(ep);
    }
}
