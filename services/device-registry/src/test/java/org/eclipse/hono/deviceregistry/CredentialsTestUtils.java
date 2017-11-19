/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import static org.eclipse.hono.util.CredentialsConstants.*;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_DEVICE_ID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Utility methods for testing functionality around credentials management.
 *
 */
public final class CredentialsTestUtils {

    private CredentialsTestUtils() {
        // prevent instantiation
    }

    /**
     * Creates a credentials object for a device and auth ID.
     * <p>
     * The credentials created are of type <em>hashed-password</em> and
     * have a validity period of <em>2017-05-01T14:00:00+01:00</em> to
     * <em>2037-06-01T14:00:00+01:00</em>.
     * 
     * @param deviceId The device identifier.
     * @param authId The authentication identifier.
     * @return The credentials.
     */
    public static JsonObject buildCredentialsPayloadHashedPassword(final String deviceId, final String authId) {
        final JsonObject secret = new JsonObject().
                put(FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(FIELD_SECRETS_HASH_FUNCTION, "sha-512").
                put(FIELD_SECRETS_SALT, "aG9ubw==").
                put(FIELD_SECRETS_PWD_HASH, "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA==");
        final JsonObject credPayload = new JsonObject().
                put(FIELD_DEVICE_ID, deviceId).
                put(FIELD_TYPE, SECRETS_TYPE_HASHED_PASSWORD).
                put(FIELD_AUTH_ID, authId).
                put(FIELD_SECRETS, new JsonArray().add(secret));
        return credPayload;
    }

    /**
     * Creates a credentials object for a device and auth ID.
     * <p>
     * The credentials created are of type <em>psk</em> and
     * have a validity period of <em>2017-05-01T14:00:00+01:00</em> to
     * <em>2037-06-01T14:00:00+01:00</em>.
     * 
     * @param deviceId The device identifier.
     * @param authId The authentication identifier.
     * @return The credentials.
     */
    public static JsonObject buildCredentialsPayloadPresharedKey(final String deviceId, final String authId) {
        final JsonObject secret = new JsonObject().
                put(FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(FIELD_SECRETS_KEY, "aG9uby1zZWNyZXQ="); // base64 "hono-secret"
        final JsonObject credPayload = new JsonObject().
                put(FIELD_DEVICE_ID, deviceId).
                put(FIELD_TYPE, SECRETS_TYPE_PRESHARED_KEY).
                put(FIELD_AUTH_ID, authId).
                put(FIELD_SECRETS, new JsonArray().add(secret));
        return credPayload;
    }


}
