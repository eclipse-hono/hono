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

import java.util.Objects;

import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Credentials API.
 */
public final class CredentialsConstants extends RequestResponseApiConstants {

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

    /**
     * Request actions that belong to the Credentials API.
     */
    public enum CredentialsAction {
        get, add, update, remove, unknown;

        /**
         * Construct a CredentialsAction from a subject.
         *
         * @param subject The subject from which the CredentialsAction needs to be constructed.
         * @return CredentialsAction The CredentialsAction as enum, or {@link CredentialsAction#unknown} otherwise.
         */
        public static CredentialsAction from(final String subject) {
            if (subject != null) {
                try {
                    return CredentialsAction.valueOf(subject);
                } catch (final IllegalArgumentException e) {
                }
            }
            return unknown;
        }

        /**
         * Helper method to check if a subject is a valid Credentials API action.
         *
         * @param subject The subject to validate.
         * @return boolean {@link Boolean#TRUE} if the subject denotes a valid action, {@link Boolean#FALSE} otherwise.
         */
        public static boolean isValid(final String subject) {
            return CredentialsAction.from(subject) != CredentialsAction.unknown;
        }
    }


    private CredentialsConstants() {
        // prevent instantiation
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
    public static JsonObject getServiceGetRequestAsJson(
            final String tenantId,
            final String deviceId,
            final String authId,
            final String type) {

        Objects.requireNonNull(tenantId);

        final JsonObject payload = new JsonObject();
        if (deviceId != null) {
            payload.put(FIELD_PAYLOAD_DEVICE_ID, deviceId);
        }
        if (authId != null) {
            payload.put(FIELD_AUTH_ID, authId);
        }
        if (type != null) {
            payload.put(FIELD_TYPE, type);
        }

        return EventBusMessage.forOperation(CredentialsAction.get.toString())
                .setTenant(tenantId)
                .setJsonPayload(payload)
                .toJson();
    }

}

