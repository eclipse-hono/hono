/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import io.vertx.core.json.DecodeException;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Tenant API.
 */

public final class TenantConstants extends RequestResponseApiConstants {

    /* tenant actions */
    public static final String ACTION_GET                        = "get";
    public static final String ACTION_ADD                        = "add";
    public static final String ACTION_UPDATE                     = "update";
    public static final String ACTION_REMOVE                     = "remove";


    /* message payload fields */
    public static final String FIELD_ENABLED                     = "enabled";
    public static final String FIELD_ADAPTERS                    = "adapters";
    public static final String FIELD_ADAPTERS_TYPE               = "type";
    public static final String FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED = "device-authentication-required";


    private static final List<String> ACTIONS = Arrays.asList(ACTION_GET, ACTION_ADD, ACTION_UPDATE, ACTION_REMOVE);

    /**
     * The name of the Tenant API endpoint.
     */
    public static final String TENANT_ENDPOINT = "tenant";

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TENANT_IN = "tenant.in";

    /**
     * Creates a JSON object from a Tenant API request message.
     *
     * @param message The AMQP 1.0 tenant request message.
     * @return The tenant message created from the AMQP message.
     * @throws NullPointerException if message is {@code null}.
     * @throws DecodeException if the message contains a body that cannot be parsed into a JSON object.
     */
    public static JsonObject getTenantMsg(final Message message) {
        Objects.requireNonNull(message);
        final String subject = message.getSubject();
        final String tenantId = MessageHelper.getTenantIdAnnotation(message);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getServiceRequestAsJson(subject, tenantId, payload);
    }

    /**
     * Gets a JSON object representing the reply to a Tenant API request via the vert.x event bus.
     *
     * @param tenantId The tenant for which the message was processed.
     * @param result The result to return to the sender of the request.
     * @return JsonObject The JSON reply object.
     */
    public static final JsonObject getServiceReplyAsJson(final String tenantId, final TenantResult result) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(RequestResponseApiConstants.FIELD_TENANT_ID, tenantId);

        jsonObject.put("status", result.getStatus());
        if (result.getPayload() != null) {
            jsonObject.put(RequestResponseApiConstants.FIELD_PAYLOAD, result.getPayload());
        }

        return jsonObject;
    }

    public static boolean isValidAction(final String subject) {
        if (subject == null) {
            return false;
        } else {
            return ACTIONS.contains(subject);
        }
    }
}
