/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Helper class for working with hostnames.
 */
public final class Hostnames {

    private static final String HOSTNAME;

    static {

        // works on Linux
        String hostname = System.getenv("HOSTNAME");

        if (hostname == null) {
            // this can produce all kinds of unexpected results
            // but better than nothing
            try {
                final InetAddress localhost = InetAddress.getLocalHost();
                hostname = localhost.getHostAddress();
            } catch (final UnknownHostException e) {
                // fall through
            }
        }

        if (hostname == null) {
            // better than "null"
            hostname = "localhost";
        }

        HOSTNAME = hostname;
    }

    private Hostnames() {
    }

    /**
     * Return the hostname.
     * <p>
     * This method tries to get the hostname of the machine. It will try different approaches.
     * </p>
     *
     * @return The hostname, but never {@code null}.
     */
    public static String getHostname() {
        return HOSTNAME;
    }
}
