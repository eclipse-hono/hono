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

import org.eclipse.hono.server.EndpointHelper;

import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Telemetry API.
 */
public final class TelemetryConstants {

    public static final String TELEMETRY_ENDPOINT             = "telemetry";
    public static final String PATH_SEPARATOR = "/";
    public static final String NODE_ADDRESS_TELEMETRY_PREFIX  = TELEMETRY_ENDPOINT + PATH_SEPARATOR;
    /**
     * The vert.x event bus address to which inbound telemetry data is published.
     */
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_IN = "telemetry.in";
    /**
     * The vert.x event bus address to which link control messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL = TELEMETRY_ENDPOINT + EndpointHelper.EVENT_BUS_ADDRESS_LINK_CONTROL_SUFFIX;
    /**
     * The vert.x event bus address to which flow control messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_FLOW_CONTROL = TELEMETRY_ENDPOINT + EndpointHelper.EVENT_BUS_ADDRESS_FLOW_CONTROL_SUFFIX;

    private TelemetryConstants() {
    }

    /**
     * Gets the Vert.x event bus address to use for sending telemetry messages downstream.
     * 
     * @param instanceId The instance number of the endpoint. 
     * @return The address.
     */
    public static final String getDownstreamMessageAddress(final int instanceNo) {
        return String.format("%s.%d", EVENT_BUS_ADDRESS_TELEMETRY_IN, instanceNo);
    }

    public static JsonObject getTelemetryMsg(final String messageId, final String linkId) {

        return new JsonObject()
                .put(EndpointHelper.FIELD_NAME_LINK_ID, linkId)
                .put(EndpointHelper.FIELD_NAME_MSG_UUID, messageId);
    }
}
