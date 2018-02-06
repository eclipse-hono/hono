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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.TenantConstants;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Utility methods for testing functionality around credentials management.
 *
 */
public final class DeviceRegistryTestUtils {

    private DeviceRegistryTestUtils() {
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
                put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION, "sha-512").
                put(CredentialsConstants.FIELD_SECRETS_SALT, "aG9ubw==").
                put(CredentialsConstants.FIELD_SECRETS_PWD_HASH, "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA==");
        final JsonObject credPayload = new JsonObject().
                put(CredentialsConstants.FIELD_DEVICE_ID, deviceId).
                put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD).
                put(CredentialsConstants.FIELD_AUTH_ID, authId).
                put(CredentialsConstants.FIELD_SECRETS, new JsonArray().add(secret));
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
                put(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(CredentialsConstants.FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(CredentialsConstants.FIELD_SECRETS_KEY, "aG9uby1zZWNyZXQ="); // base64 "hono-secret"
        final JsonObject credPayload = new JsonObject().
                put(CredentialsConstants.FIELD_DEVICE_ID, deviceId).
                put(CredentialsConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY).
                put(CredentialsConstants.FIELD_AUTH_ID, authId).
                put(CredentialsConstants.FIELD_SECRETS, new JsonArray().add(secret));
        return credPayload;
    }

    /**
     * Creates a tenant object for a tenantId.
     * <p>
     * The tenant created is configured for the http and the mqtt adapter.
     *
     * @param tenantId The tenant identifier.
     * @return The tenant object.
     */
    public static JsonObject buildTenantPayload(final String tenantId) {
        final JsonObject adapterDetailsHttp = new JsonObject().
                put(TenantConstants.FIELD_ADAPTERS_TYPE, "hono-http").
                put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, "true").
                put(TenantConstants.FIELD_ENABLED, "true");
        final JsonObject adapterDetailsMqtt = new JsonObject().
                put(TenantConstants.FIELD_ADAPTERS_TYPE, "hono-mqtt").
                put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, "true").
                put(TenantConstants.FIELD_ENABLED, "true");
        final JsonObject tenantPayload = new JsonObject().
                put(TenantConstants.FIELD_TENANT_ID, tenantId).
                put(TenantConstants.FIELD_ENABLED, "true").
                put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterDetailsHttp).add(adapterDetailsMqtt));
        return tenantPayload;
    }

    /**
     * Reads the contents from a file using this class' class loader.
     * 
     * @param resourceName The name of the resource to load.
     * @return The contents of the file.
     * @throws IOException if the file cannot be read.
     */
    public static Buffer readFile(final String resourceName) throws IOException {

        final Buffer result = Buffer.buffer();
        try (InputStream is = DeviceRegistryTestUtils.class.getResourceAsStream(resourceName)) {
            int bytesRead = 0;
            final byte[] readBuffer = new byte[4096];
            while ((bytesRead = is.read(readBuffer)) != -1) {
                result.appendBytes(readBuffer, 0, bytesRead);
            }
        }
        return result;
    }

    /**
     * A simple implementation of subtree containment: all entries of the JsonObject that is tested to be contained
     * must be contained in the other JsonObject as well. Nested JsonObjects are treated the same by recursively calling
     * this method to test the containment.
     * Note that currently JsonArrays need to be equal and are not tested for containment (not necessary for our purposes
     * here).
     * @param jsonObject The JsonObject that must fully contain the other JsonObject (but may contain more entries as well).
     * @param jsonObjectToBeContained The JsonObject that needs to be fully contained inside the other JsonObject.
     * @return The result of the containment test.
     */
    public static boolean testJsonObjectToBeContained(final JsonObject jsonObject, final JsonObject jsonObjectToBeContained) {
        if (jsonObjectToBeContained == null) {
            return true;
        }
        if (jsonObject == null) {
            return false;
        }
        AtomicBoolean containResult = new AtomicBoolean(true);

        jsonObjectToBeContained.forEach(entry -> {
            if (!jsonObject.containsKey(entry.getKey())) {
                containResult.set(false);
            } else {
                if (entry.getValue() == null) {
                    if (jsonObject.getValue(entry.getKey()) != null) {
                        containResult.set(false);
                    }
                } else if (entry.getValue() instanceof JsonObject) {
                    if (!(jsonObject.getValue(entry.getKey()) instanceof JsonObject)) {
                        containResult.set(false);
                    } else {
                        if (!testJsonObjectToBeContained((JsonObject)entry.getValue(),
                                (JsonObject)jsonObject.getValue(entry.getKey()))) {
                            containResult.set(false);
                        }
                    }
                } else {
                    if (!(entry.getValue().equals(jsonObject.getValue(entry.getKey())))) {
                        containResult.set(false);
                    }
                }
            }
        });
        return containResult.get();
    }
}
