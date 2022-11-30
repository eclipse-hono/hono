/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
 * Denotes the quality-of-service level (in Hono's terms) used for sending a message.
 */
public enum QoS {

    /**
     * Indicates that the message will be sent at most once, i.e. the sender does not care about
     * whether the message is successfully transferred and/or processed by receivers.
     */
    AT_MOST_ONCE,
    /**
     * Indicates that a message might be sent multiple times, for example if the sender believes
     * that the message might have not reached its recipient due to a network problem.
     */
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
