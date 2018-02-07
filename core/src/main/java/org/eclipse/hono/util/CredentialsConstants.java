/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
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

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Credentials API.
 */
public final class CredentialsConstants extends RequestResponseApiConstants {
    /* credentials actions */
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
    /**
     * The vert.x event bus address to which inbound credentials messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_CREDENTIALS_IN = "credentials.in";

    private static final List<String> OPERATIONS = Arrays.asList(OPERATION_GET, OPERATION_ADD, OPERATION_UPDATE, OPERATION_REMOVE);

    private CredentialsConstants() {
        // prevent instantiation
    }

    /**
     * Creates a JSON message for an AMQP message representing a request to
     * a Credentials API operation.
     * 
     * @param message The request message.
     * @param target The target address that the request has been sent to.
     * @return The JSON object to be sent over the vert.x event bus in order to process the request.
     * @throws DecodeException if the message's payload is not valid JSON.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static JsonObject getCredentialsMsg(final Message message, final ResourceIdentifier target) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(target);
        final String subject = message.getSubject();
        final String tenantId = target.getTenantId();
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getServiceRequestAsJson(subject, tenantId, null, payload);
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
     * @return JsonObject The JSON object for the request that is to be sent via the vert.x event bus.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public static JsonObject getServiceGetRequestAsJson(final String tenantId, final String deviceId, final String authId,
                                                        final String type) {
        Objects.requireNonNull(tenantId);

        final JsonObject payload = new JsonObject();
        if (deviceId != null) {
            payload.put(FIELD_DEVICE_ID, deviceId);
        }
        if (authId != null) {
            payload.put(FIELD_AUTH_ID, authId);
        }
        if (type != null) {
            payload.put(FIELD_TYPE, type);
        }

        return getServiceRequestAsJson(OPERATION_GET, tenantId, null, payload);
    }

    /**
     * Checks if a given subject represents a Credentials API operation.
     * 
     * @param subject The subject to check.
     * @return {@code true} if the subject is one of the Credential API's operations.
     */
    public static boolean isValidSubject(final String subject) {
        if (subject == null) {
            return false;
        } else {
            return OPERATIONS.contains(subject);
        }
    }
}

