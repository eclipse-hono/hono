/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
 * Commands utility methods used throughout the Command and Control API.
 */
public class CommandConstants {

    private CommandConstants() {
        // prevent instantiation
    }

    /**
     * The name of the Command and Control API endpoint.
     */
    public static final String COMMAND_ENDPOINT = "control";

    /**
     * The short name of the control endpoint.
     */
    public static final String COMMAND_ENDPOINT_SHORT = "c";

    /**
     * The part of the address for a command response between a device and an adapter, which identifies the request.
     */
    public static final String COMMAND_RESPONSE_REQUEST_PART = "req";


    /**
     * Short version of COMMAND_RESPONSE_REQUEST_PART.
     */
    public static final String COMMAND_RESPONSE_REQUEST_PART_SHORT = "q";

    /**
     * The part of the address for a command response between a device and an adapter, which identifies the response.
     */
    public static final String COMMAND_RESPONSE_RESPONSE_PART = "res";

    /**
     * Short version of COMMAND_RESPONSE_RESPONSE_PART.
     */
    public static final String COMMAND_RESPONSE_RESPONSE_PART_SHORT = "s";

    /**
     * Position of the status code in the MQTT command response topic.
     * {@code control/[tenant]/[device-id]/res/<req-id>/<status>}
     */
    public static final int TOPIC_POSITION_RESPONSE_STATUS = 5;

    /**
     * Position of the request id in the MQTT command response topic.
     * {@code control/[tenant]/[device-id]/res/<req-id>/<status>}
     */
    public static final int TOPIC_POSITION_RESPONSE_REQ_ID = 4;

    /**
     * Returns true if the passed endpoint denotes a command endpoint (full or short version).
     *
     * @param endpoint The endpoint as a string.
     * @return {@code true} if the endpoint is a command endpoint.
     */
    public static final boolean isCommandEndpoint(final String endpoint) {
        return COMMAND_ENDPOINT.equals(endpoint) || COMMAND_ENDPOINT_SHORT.equals(endpoint);
    }

}
