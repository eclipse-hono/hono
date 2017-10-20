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

import static org.eclipse.hono.util.MessageHelper.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Registration API.
 */
public final class RegistrationConstants extends RequestResponseApiConstants {

    /* registration actions */
    public static final String ACTION_ASSERT     = "assert";
    public static final String ACTION_REGISTER   = "register";
    public static final String ACTION_GET        = "get";
    public static final String ACTION_ENABLED    = "enabled";
    public static final String ACTION_DEREGISTER = "deregister";
    public static final String ACTION_UPDATE     = "update";


    /* JSON field names */
    public static final String FIELD_ASSERTION                   = "assertion";
    public static final String FIELD_DATA                        = "data";


    public static final String REGISTRATION_ENDPOINT             = "registration";
    public static final String PATH_SEPARATOR                    = "/";
    public static final String NODE_ADDRESS_REGISTRATION_PREFIX  = REGISTRATION_ENDPOINT + PATH_SEPARATOR;

    private static final List<String> ACTIONS     = Arrays.asList(ACTION_ASSERT, ACTION_REGISTER,
            ACTION_GET, ACTION_DEREGISTER, ACTION_UPDATE, ACTION_ENABLED);

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_REGISTRATION_IN = "registration.in";


    private RegistrationConstants() {
        // prevent instantiation
    }

    /**
     * Checks if a given string represents a valid action.
     * 
     * @param action The string to check.
     * @return {@code true} if the given string is a supported action.
     */
    public static boolean isValidAction(final String action) {
        if (action == null) {
            return false;
        } else {
            return ACTIONS.contains(action);
        }
    }

    /**
     * Creates a JSON object from a Registration API request message.
     *
     * @param message The AMQP 1.0 registration request message.
     * @return The registration message created from the AMQP message.
     * @throws NullPointerException if message is {@code null}.
     * @throws DecodeException if the message contains a body that cannot be parsed into a JSON object.
     */
    public static JsonObject getRegistrationMsg(final Message message) {
        Objects.requireNonNull(message);
        final String deviceId = MessageHelper.getDeviceIdAnnotation(message);
        final String tenantId = MessageHelper.getTenantIdAnnotation(message);
        final String key = getKey(message);
        final String operation = message.getSubject();
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getServiceRequestAsJson(operation, tenantId, deviceId, key, payload);
    }

    /**
     * Build a Json object as a reply to a registration request via the vert.x event bus.
     *
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param result The {@link RegistrationResult} object with the payload for the reply object.
     * @return JsonObject The json reply object that is to be sent back via the vert.x event bus.
     */
    public static JsonObject getServiceReplyAsJson(final String tenantId, final String deviceId, final RegistrationResult result) {
        return getServiceReplyAsJson(result.getStatus(), tenantId, deviceId, result.getPayload());
    }

    /**
     * Checks if a JSON message contains a given status code.
     *  
     * @param msg The message to check.
     * @param expectedStatus The expected status code.
     * @return {@code true} if the given message has a string typed <em>status</em> property and the property's value is
     *                      is the string representation of the expected status.
     */
    public static boolean hasStatus(final JsonObject msg, final int expectedStatus) {

        return Objects.requireNonNull(msg).getInteger(MessageHelper.APP_PROPERTY_STATUS).equals(expectedStatus);
    }


    private static String getKey(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_KEY, String.class);
    }
}
