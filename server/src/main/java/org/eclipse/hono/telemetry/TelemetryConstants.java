/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.telemetry;

/**
 * Constants used throughout the Telemetry API.
 *
 */
public final class TelemetryConstants {

    public static final String NODE_ADDRESS_TELEMETRY_PREFIX = "telemetry/";
    /**
     * The vert.x event bus address inbound telemetry data is published on.
     */
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_IN = "telemetry.in";

    private TelemetryConstants() {
    }
}
