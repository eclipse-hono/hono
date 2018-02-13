/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Tenant API.
 */

public final class TenantConstants extends RequestResponseApiConstants {

    public static final String ACTION_GET = "get";
    public static final String ACTION_ADD = "add";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_REMOVE = "remove";

    public static final String FIELD_ADAPTERS = "adapters";

    public static final String FIELD_ADAPTERS_TYPE = "type";
    public static final String FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED = "device-authentication-required";

    private static final List<String> ACTIONS = Arrays.asList(ACTION_GET, ACTION_ADD, ACTION_UPDATE, ACTION_REMOVE);

    public static final String TENANT_ENDPOINT = "tenant";

    public static final String EVENT_BUS_ADDRESS_TENANT_IN = "tenant.in";

    public static JsonObject getTenantMsg(final Message message) {
        Objects.requireNonNull(message);
        final String subject = message.getSubject();
        final String tenantId = MessageHelper.getTenantIdAnnotation(message);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getTenantRequestAsJson(subject, tenantId, payload);
    }

    private static JsonObject getTenantRequestAsJson(final String operation, final String tenantId,
                                                     final JsonObject payload) {
        final JsonObject msg = new JsonObject();
        msg.put(MessageHelper.SYS_PROPERTY_SUBJECT, operation).put(FIELD_TENANT_ID, tenantId);
        if (payload != null) {
            msg.put(TenantConstants.FIELD_PAYLOAD, payload);
        }
        return msg;
    }

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