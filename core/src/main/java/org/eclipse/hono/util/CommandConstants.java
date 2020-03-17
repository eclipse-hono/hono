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
 * Commands utility methods used throughout the Command and Control API.
 */
public class CommandConstants {

    /**
     * The name of the Command and Control API endpoint.
     */
    public static final String COMMAND_ENDPOINT = "command";

    /**
     * The short name of the control endpoint.
     */
    public static final String COMMAND_ENDPOINT_SHORT = "c";

    /**
     * The name of the Command and Control API endpoint provided by protocol adapters that use a separate endpoint for
     * command responses.
     */
    public static final String COMMAND_RESPONSE_ENDPOINT = "command_response";

    /**
     * The name of the internal Command and Control API endpoint provided by protocol adapters for delegating
     * commands from one adapter to another.
     */
    public static final String INTERNAL_COMMAND_ENDPOINT = "command_internal";

    /**
     * The name of the northbound Command and Control API request endpoint used by northbound applications.
     */
    public static final String NORTHBOUND_COMMAND_REQUEST_ENDPOINT = "command";

    /**
     * The name of the northbound Command and Control API response endpoint used by northbound applications.
     */
    public static final String NORTHBOUND_COMMAND_RESPONSE_ENDPOINT = "command_response";

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
     * {@code command/[tenant]/[device-id]/res/<req-id>/<status>}
     */
    public static final int TOPIC_POSITION_RESPONSE_STATUS = 5;

    /**
     * Position of the request id in the MQTT command response topic.
     * {@code command/[tenant]/[device-id]/res/<req-id>/<status>}
     */
    public static final int TOPIC_POSITION_RESPONSE_REQ_ID = 4;

    private CommandConstants() {
        // prevent instantiation
    }

    /**
     * Returns {@code true} if the passed endpoint denotes a command endpoint (full or short version).
     *
     * @param endpoint The endpoint as a string.
     * @return {@code true} if the endpoint is a command endpoint.
     */
    public static final boolean isCommandEndpoint(final String endpoint) {
        return COMMAND_ENDPOINT.equals(endpoint) || COMMAND_ENDPOINT_SHORT.equals(endpoint);
    }

    /**
     * Returns {@code true} if the passed endpoint denotes a command response endpoint as used by northbound applications.
     *
     * @param endpoint The endpoint as a string.
     * @return {@code true} if the endpoint is a command response endpoint.
     */
    public static boolean isNorthboundCommandResponseEndpoint(final String endpoint) {
        return CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT.equals(endpoint);
    }

}
