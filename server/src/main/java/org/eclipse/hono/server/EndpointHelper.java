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
package org.eclipse.hono.server;

import java.util.Objects;

import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; methods useful for implementing Hono {@code Endpoint}s.
 */
public final class EndpointHelper {

    public static final String EVENT_ATTACHED = "attached";
    public static final String EVENT_DETACHED = "detached";

    public static final String FIELD_NAME_CLOSE_LINK = "close-link";
    public static final String FIELD_NAME_CONNECTION_ID = "connection-id";
    public static final String FIELD_NAME_CREDIT = "credit";
    public static final String FIELD_NAME_DRAIN = "drain";
    public static final String FIELD_NAME_ENDPOINT = "endpoint";
    public static final String FIELD_NAME_EVENT = "event";
    public static final String FIELD_NAME_LINK_ID = "link-id";
    public static final String FIELD_NAME_MSG_UUID = "uuid";
    public static final String FIELD_NAME_SUSPEND = "suspend";
    public static final String FIELD_NAME_TARGET_ADDRESS = "target-address";

    public static final String HEADER_NAME_REPLY_TO = "reply-to";
    public static final String HEADER_NAME_TYPE = "type";

    public static final String RESULT_ACCEPTED = "accepted";

    public static final String MSG_TYPE_ERROR = "error";
    public static final String MSG_TYPE_FLOW_CONTROL = "flow-control";

    /**
     * The suffix to use for Vert.x event bus addresses to which downstream link control messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_LINK_CONTROL_SUFFIX = ".link.control";
    /**
     * The suffix to use for Vert.x event bus addresses to which upstream flow control messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_FLOW_CONTROL_SUFFIX = ".flow.control";

    private EndpointHelper() {
    }

    /**
     * Gets the Vert.x event bus address to use for sending flow control messages upstream.
     * 
     * @param endpointName The name of the endpoint.
     * @param instanceId The instance number of the endpoint. 
     * @return The address.
     */
    public static final String getFlowControlAddress(final String endpointName, final int instanceNo) {
        return String.format("%s%s.%d", endpointName, EVENT_BUS_ADDRESS_FLOW_CONTROL_SUFFIX, instanceNo);
    }

    /**
     * Gets the Vert.x event bus address to use for sending link control messages downstream.
     * 
     * @param endpointName The name of the endpoint.
     * @param instanceId The instance number of the endpoint. 
     * @return The address.
     */
    public static final String getLinkControlAddress(final String endpointName, final int instanceNo) {
        return String.format("%s%s.%d", endpointName, EVENT_BUS_ADDRESS_LINK_CONTROL_SUFFIX, instanceNo);
    }

    public static JsonObject getLinkAttachedMsg(final String connectionId, final String linkId, final ResourceIdentifier targetAddress) {

        return new JsonObject()
                .put(FIELD_NAME_EVENT, EVENT_ATTACHED)
                .put(FIELD_NAME_CONNECTION_ID, Objects.requireNonNull(connectionId))
                .put(FIELD_NAME_LINK_ID, Objects.requireNonNull(linkId))
                .put(FIELD_NAME_TARGET_ADDRESS, Objects.requireNonNull(targetAddress).toString());
    }

    public static JsonObject getLinkDetachedMsg(final String linkId) {

        return new JsonObject()
                .put(FIELD_NAME_EVENT, EVENT_DETACHED)
                .put(FIELD_NAME_LINK_ID, Objects.requireNonNull(linkId));
    }

    public static boolean isFlowControlMessage(final MultiMap headers) {
        return hasHeader(headers, HEADER_NAME_TYPE, MSG_TYPE_FLOW_CONTROL);
    }

    public static JsonObject getFlowControlMsg(final String linkId, final int credit, final boolean drain) {
        return new JsonObject()
                .put(FIELD_NAME_LINK_ID, linkId)
                .put(FIELD_NAME_CREDIT, credit)
                .put(FIELD_NAME_DRAIN, drain);
    }

    public static boolean isErrorMessage(final MultiMap headers) {
        return hasHeader(headers, HEADER_NAME_TYPE, MSG_TYPE_ERROR);
    }

    public static JsonObject getErrorMessage(final String linkId, final boolean closeLink) {
        return new JsonObject()
                .put(FIELD_NAME_LINK_ID, linkId)
                .put(FIELD_NAME_CLOSE_LINK, closeLink);
    }

    public static boolean hasHeader(final MultiMap headers, final String name, final String expectedValue) {
        Objects.requireNonNull(headers);
        Objects.requireNonNull(expectedValue);
        Objects.requireNonNull(name);
        return expectedValue.equals(headers.get(name));
    }

    /**
     * 
     * @param options
     * @param type
     * @return
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static DeliveryOptions addMessageTypeHeader(final DeliveryOptions options, final String type) {
        Objects.requireNonNull(options);
        return options.addHeader(HEADER_NAME_TYPE, Objects.requireNonNull(type));
    }

    /**
     * 
     * @param options
     * @param address
     * @return
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static DeliveryOptions addReplyToHeader(final DeliveryOptions options, final String address) {
        Objects.requireNonNull(options);
        return options.addHeader(HEADER_NAME_REPLY_TO, Objects.requireNonNull(address));
    }

    public static String getReplyToAddress(final MultiMap headers) {
        return Objects.requireNonNull(headers).get(HEADER_NAME_REPLY_TO);
    }
}
