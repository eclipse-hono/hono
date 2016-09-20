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

import java.util.Objects;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

/**
 * Constants & utility methods used throughout the Telemetry API.
 */
public final class TelemetryConstants {

    public static final String EVENT_DETACHED = "detached";
    public static final String EVENT_ATTACHED = "attached";
    public static final String FIELD_NAME_LINK_ID = "link-id";
    public static final String FIELD_NAME_CLOSE_LINK = "close-link";
    public static final String FIELD_NAME_CONNECTION_ID = "connection-id";
    public static final String FIELD_NAME_CREDIT = "credit";
    public static final String FIELD_NAME_ENDPOINT = "endpoint";
    public static final String FIELD_NAME_EVENT = "event";
    public static final String FIELD_NAME_MSG_UUID = "uuid";
    public static final String FIELD_NAME_SUSPEND = "suspend";
    public static final String FIELD_NAME_TARGET_ADDRESS = "target-address";
    public static final String HEADER_NAME_REPLY_TO = "reply-to";
    public static final String RESULT_ACCEPTED = "accepted";
    public static final String MSG_TYPE_ERROR = "error";
    public static final String MSG_TYPE_FLOW_CONTROL = "flow-control";
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
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL = "telemetry.link.control";
    /**
     * The vert.x event bus address to which credit replenishment messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TELEMETRY_FLOW_CONTROL = "telemetry.flow.control";

    private TelemetryConstants() {
    }

    public static JsonObject getLinkAttachedMsg(final String connectionId, final String linkId, final ResourceIdentifier targetAddress) {
        JsonObject msg = new JsonObject();
        msg.put(FIELD_NAME_EVENT, EVENT_ATTACHED);
        msg.put(FIELD_NAME_CONNECTION_ID, Objects.requireNonNull(connectionId));
        msg.put(FIELD_NAME_LINK_ID, Objects.requireNonNull(linkId));
        msg.put(FIELD_NAME_TARGET_ADDRESS, Objects.requireNonNull(targetAddress).toString());
        return msg;
    }

    public static JsonObject getLinkDetachedMsg(final String linkId) {
        JsonObject msg = new JsonObject();
        msg.put(FIELD_NAME_EVENT, EVENT_DETACHED);
        msg.put(FIELD_NAME_LINK_ID, Objects.requireNonNull(linkId));
        return msg;
    }

    public static JsonObject getTelemetryMsg(final String messageId, final String linkId) {
        JsonObject msg = new JsonObject();
        msg.put(FIELD_NAME_LINK_ID, linkId);
        msg.put(FIELD_NAME_MSG_UUID, messageId);
        return msg;
    }

    public static boolean isFlowControlMessage(final JsonObject msg) {
        Objects.requireNonNull(msg);
        return msg.containsKey(MSG_TYPE_FLOW_CONTROL);
    }

    public static JsonObject getCreditReplenishmentMsg(final String linkId, final int credit) {
        return new JsonObject().put(
                MSG_TYPE_FLOW_CONTROL,
                new JsonObject()
                    .put(FIELD_NAME_LINK_ID, linkId)
                    .put(FIELD_NAME_CREDIT, credit));
    }

    public static JsonObject getErrorMessage(final String linkId, final boolean closeLink) {
        return new JsonObject().put(
                MSG_TYPE_ERROR,
                new JsonObject().put(FIELD_NAME_CLOSE_LINK, closeLink)
                    .put(FIELD_NAME_LINK_ID, linkId));
    }

    public static boolean isErrorMessage(final JsonObject msg) {
        Objects.requireNonNull(msg);
        return msg.containsKey(MSG_TYPE_ERROR);
    }

    public static DeliveryOptions addReplyToHeader(final DeliveryOptions options, final String address) {
        Objects.requireNonNull(options);
        return options.addHeader(HEADER_NAME_REPLY_TO, address);
    }
}
