/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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
 * Constants &amp; utility methods used throughout the Credentials API.
 */
public final class CredentialsConstants extends RequestResponseApiConstants {
    /* registration actions */
    public static final String OPERATION_GET                     = "get";
    public static final String OPERATION_ADD                     = "add";
    public static final String OPERATION_UPDATE                  = "update";
    public static final String OPERATION_REMOVE                  = "remove";

    /* message payload fields */
    public static final String FIELD_TYPE                        = "type";
    public static final String FIELD_AUTH_ID                     = "auth-id";
    public static final String FIELD_SECRETS                     = "secrets";
    public static final String FIELD_CREDENTIALS_TOTAL           = "total";

    /* secrets fields */
    public static final String FIELD_SECRETS_PWD_HASH            = "pwd-hash";
    public static final String FIELD_SECRETS_SALT                = "salt";
    public static final String FIELD_SECRETS_HASH_FUNCTION       = "hash-function";
    public static final String FIELD_SECRETS_KEY                 = "key";
    public static final String FIELD_SECRETS_NOT_BEFORE          = "not-before";
    public static final String FIELD_SECRETS_NOT_AFTER           = "not-after";

    public static final String CREDENTIALS_ENDPOINT              = "credentials";

    public static final String SECRETS_TYPE_HASHED_PASSWORD      = "hashed-password";
    public static final String SECRETS_TYPE_PRESHARED_KEY        = "psk";
    public static final String SPECIFIER_WILDCARD                = "*";

    /**
     * The name of the default hash function to use for hashed passwords if not set explicitly.
     */
    public static final String DEFAULT_HASH_FUNCTION             ="sha-256";

    private static final List<String> OPERATIONS = Arrays.asList(OPERATION_GET, OPERATION_ADD, OPERATION_UPDATE, OPERATION_REMOVE);

    /**
     * The vert.x event bus address to which inbound credentials messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_CREDENTIALS_IN = "credentials.in";

    private CredentialsConstants() {
        // prevent instantiation
    }

    public static JsonObject getCredentialsMsg(final Message message) {
        Objects.requireNonNull(message);
        final String subject = message.getSubject();
        final String tenantId = MessageHelper.getTenantIdAnnotation(message);
        final String deviceId = MessageHelper.getDeviceIdAnnotation(message);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getServiceRequestAsJson(subject, tenantId, deviceId, payload);
    }

    /**
     * Gets a JSON object representing the reply to a credentials request via the vert.x event bus.
     *
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param result The result to return to the sender of the request.
     * @return JsonObject The JSON reply object.
     */
    public static JsonObject getServiceReplyAsJson(final String tenantId, final String deviceId, final CredentialsResult<JsonObject> result) {
        return RequestResponseApiConstants.getServiceReplyAsJson(result.getStatus(), tenantId, deviceId, result.getPayload());
    }

    /**
     * Build a Json object as a request for internal communication via the vert.x event bus.
     * Clients use this object to build their request that is sent to the processing service.
     *
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param authId The authId of the device that the message relates to.
     * @param type The type of credentials that the message relates to.
     * @param payload The payload from the request that is passed to the processing service. Must not be null.
     * @return JsonObject The json object for the request that is to be sent via the vert.x event bus.
     * @throws NullPointerException if tenant or payload is {@code null}.
     */
    public static JsonObject getServiceGetRequestAsJson(final String tenantId, final String deviceId, final String authId,
                                                        final String type, final JsonObject payload) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(payload);

        final JsonObject msg = new JsonObject();
        msg.put(MessageHelper.SYS_PROPERTY_SUBJECT, OPERATION_GET);
        msg.put(FIELD_TENANT_ID, tenantId);
        if (deviceId != null) {
            payload.put(FIELD_DEVICE_ID, deviceId);
        }
        if (authId != null) {
            payload.put(FIELD_AUTH_ID, authId);
        }
        if (type != null) {
            payload.put(FIELD_TYPE, type);
        }
        msg.put(FIELD_PAYLOAD, payload);

        return msg;
    }

    public static boolean isValidSubject(final String subject) {
        if (subject == null) {
            return false;
        } else {
            return OPERATIONS.contains(subject);
        }
    }


}
