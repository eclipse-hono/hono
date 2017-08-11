/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.util;

/**
 * Helper class for port configuration.
 */
public abstract class PortConfigurationHelper {

    private static final int MAX_PORT_NO = 65535;
    private static final int MIN_PORT_NO = 0;

    private PortConfigurationHelper() {
        // prevent instantiation
    }

    /**
     * Checks if a given port number is valid.
     *
     * @param port The port number.
     * @return {@code true} if port &gt;= 0 and port &lt;= 65535.
     */
    public static boolean isValidPort(final int port) {
        return port >= MIN_PORT_NO && port <= MAX_PORT_NO;
    }

}
