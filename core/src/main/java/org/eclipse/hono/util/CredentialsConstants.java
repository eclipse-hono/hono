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

import io.vertx.core.json.JsonObject;
import org.apache.qpid.proton.message.Message;

import java.util.*;

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

    /* secrets fields */
    public static final String FIELD_SECRETS_PWD_HASH            = "pwd-hash";
    public static final String FIELD_SECRETS_SALT                = "salt";
    public static final String FIELD_SECRETS_HASH_FUNCTION       = "hash-function";
    public static final String FIELD_SECRETS_NOT_BEFORE          = "not-before";
    public static final String FIELD_SECRETS_NOT_AFTER           = "not-after";

    public static final String CREDENTIALS_ENDPOINT              = "credentials";

    public static final String SECRETS_TYPE_HASHED_PASSWORD      = "hashed-password";
    public static final String SECRETS_TYPE_PRESHARED_KEY        = "psk";

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
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getServiceRequestAsJson(subject, tenantId, null, payload);
    }

    /**
     * Build a Json object as a reply to a credentials request via the vert.x event bus.
     *
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param result The {@link RegistrationResult} object with the payload for the reply object.
     * @return JsonObject The json reply object that is to be sent back via the vert.x event bus.
     */
    public static JsonObject getServiceReplyAsJson(final String tenantId, final String deviceId, final CredentialsResult result) {
        return getServiceReplyAsJson(result.getStatus(), tenantId, deviceId, result.getPayload());
    }

    public static boolean isValidSubject(final String subject) {
        if (subject == null) {
            return false;
        } else {
            return OPERATIONS.contains(subject);
        }
    }


}
