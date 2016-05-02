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

import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.json.JsonObject;

/**
 * Constants & utility methods used throughout the Telemetry API.
 */
public final class TelemetryConstants {

    public static final String FIELD_NAME_MSG_UUID = "uuid";
    public static final String FIELD_NAME_ENDPOINT = "endpoint";
    public static final String RESULT_ACCEPTED = "accepted";
    public static final String RESULT_ERROR = "error";
    public static final String TELEMETRY_ENDPOINT             = "telemetry";
    public static final String PATH_SEPARATOR = "/";
    public static final String NODE_ADDRESS_TELEMETRY_PREFIX  = TELEMETRY_ENDPOINT + PATH_SEPARATOR;
    /**
     * The vert.x event bus address inbound telemetry data is published on.
     */
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_IN = "telemetry.in";

    private TelemetryConstants() {
    }

    public static JsonObject getTelemetryMsg(final String messageId, final ResourceIdentifier messageAddress) {
        JsonObject msg = new JsonObject();
        msg.put(FIELD_NAME_MSG_UUID, messageId);
        msg.put(MessageHelper.APP_PROPERTY_TENANT_ID, messageAddress.getTenantId());
        return msg;
    }
}
