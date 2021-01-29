/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
 * Denotes the QoS level (in Hono's terms) with which a message was sent by the device.
 */
public enum QoS {

    AT_MOST_ONCE,
    AT_LEAST_ONCE;

    /**
     * Gets the quality of service corresponding to a given integer.
     *
     * @param code The code to get the qos for.
     * @return {@link #AT_MOST_ONCE} if code is 0, {@link #AT_LEAST_ONCE} if code is 1, otherwise {@code null}.
     */
    public static QoS from(final int code) {
        switch (code) {
        case 0: return AT_MOST_ONCE;
        case 1: return AT_LEAST_ONCE;
        default: return null;
        }
    }
}
