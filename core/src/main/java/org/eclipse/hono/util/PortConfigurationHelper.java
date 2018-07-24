/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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
