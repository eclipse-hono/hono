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

import org.eclipse.hono.util.Constants;

/**
 * Constants &amp; utility methods used throughout the Telemetry API.
 */
public final class TelemetryConstants {

    public static final String TELEMETRY_ENDPOINT             = "telemetry";
    public static final String NODE_ADDRESS_TELEMETRY_PREFIX  = TELEMETRY_ENDPOINT + Constants.DEFAULT_PATH_SEPARATOR;

    private TelemetryConstants() {
    }

}
