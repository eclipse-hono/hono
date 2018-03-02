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

import java.util.Objects;
import io.vertx.core.json.DecodeException;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Tenant API.
 */

public final class TenantConstants extends RequestResponseApiConstants {

    /* message payload fields */
    public static final String FIELD_ADAPTERS                    = "adapters";
    public static final String FIELD_ADAPTERS_TYPE               = "type";
    public static final String FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED = "device-authentication-required";

    public static final String FIELD_RESPONSE_STATUS = "status";

    /**
     * The name of the Tenant API endpoint.
     */
    public static final String TENANT_ENDPOINT = "tenant";

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_TENANT_IN = "tenant.in";

    /**
     * Request actions that belong to the Tenant API.
     */
    public enum TenantAction {
        get, add, update, remove, unknown;

        /**
         * Construct a TenantAction from a subject.
         *
         * @param subject The subject from which the TenantAction needs to be constructed.
         * @return TenantAction The TenantAction as enum, or {@link TenantAction#unknown} otherwise.
         */
        public static TenantAction from(final String subject) {
            if (subject != null) {
                try {
                    return TenantAction.valueOf(subject);
                } catch (final IllegalArgumentException e) {
                }
            }
            return unknown;
        }

        /**
         * Helper method to check if a subject is a valid Tenant API action.
         *
         * @param subject The subject to validate.
         * @return boolean {@link Boolean#TRUE} if the subject denotes a valid action, {@link Boolean#FALSE} otherwise.
         */
        public static boolean isValid(final String subject) {
            return TenantAction.from(subject) != TenantAction.unknown;
        }
    }

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
        final String tenantId = MessageHelper.getTenantId(message);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getServiceRequestAsJson(subject, tenantId, payload);
    }

    /**
     * Gets a JSON object representing the reply to a Tenant API request via the vert.x event bus.
     *
     * @param tenantId The tenant for which the message was processed. If null, the value for the key
     *                 {@link RequestResponseApiConstants#FIELD_TENANT_ID} in the reply will be null, too.
     * @param tenantResult The result to return to the sender of the request.
     * @return JsonObject The JSON reply object.
     * @throws NullPointerException If tenantResult is null.
     */
    public static JsonObject getServiceReplyAsJson(final String tenantId, final TenantResult tenantResult) {
        Objects.requireNonNull(tenantResult);
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(RequestResponseApiConstants.FIELD_TENANT_ID, tenantId);

        jsonObject.put(FIELD_RESPONSE_STATUS, tenantResult.getStatus());
        if (tenantResult.getPayload() != null) {
            jsonObject.put(RequestResponseApiConstants.FIELD_PAYLOAD, tenantResult.getPayload());
        }

        return jsonObject;
    }
}
