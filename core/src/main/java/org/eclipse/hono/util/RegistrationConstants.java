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

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * Constants &amp; utility methods used throughout the Registration API.
 */
public final class RegistrationConstants extends RequestResponseApiConstants {

    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>assert device registration</em> operation.
     */
    public static final String ACTION_ASSERT     = "assert";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>register device</em> operation.
     */
    public static final String ACTION_REGISTER   = "register";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>get registration information</em> operation.
     */
    public static final String ACTION_GET        = "get";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>deregister device</em> operation.
     */
    public static final String ACTION_DEREGISTER = "deregister";
    /**
     * The AMQP 1.0 <em>subject</em> to use for the <em>update device registration</em> operation.
     */
    public static final String ACTION_UPDATE     = "update";

    /**
     * The name of the AMQP 1.0 message application property containing the id of the gateway
     * that wants to report data on behalf of another device.
     */
    public static final String APP_PROPERTY_GATEWAY_ID = "gateway_id";

    /**
     * The name of the field in a response to the <em>assert device registration</em> operation
     * that contains the registration status assertion.
     */
    public static final String FIELD_ASSERTION    = "assertion";
    /**
     * The name of the field in a response to the <em>get registration information</em> operation
     * that contains a device's registration information.
     */
    public static final String FIELD_DATA         = "data";
    /**
     * The name of the field in a device's registration information that contains
     * <em>defaults</em> to be used by protocol adapters when processing messages published
     * by the device.
     */
    public static final String FIELD_DEFAULTS     = "defaults";

    /**
     * The name of the Device Registration API endpoint.
     */
    public static final String REGISTRATION_ENDPOINT = "registration";

    private static final List<String> ACTIONS = Arrays.asList(ACTION_ASSERT, ACTION_REGISTER,
            ACTION_GET, ACTION_DEREGISTER, ACTION_UPDATE);

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
     * @param target The target address that the request has been sent to.
     * @return The registration message created from the AMQP message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws DecodeException if the message contains a body that cannot be parsed into a JSON object.
     */
    public static JsonObject getRegistrationMsg(final Message message, final ResourceIdentifier target) {

        Objects.requireNonNull(message);
        Objects.requireNonNull(target);
        final String subject = message.getSubject();
        final String tenantId = target.getTenantId();
        final String deviceId = MessageHelper.getDeviceId(message);
        final String gatewayId = MessageHelper.getApplicationProperty(message.getApplicationProperties(),
                APP_PROPERTY_GATEWAY_ID, String.class);
        final JsonObject payload = MessageHelper.getJsonPayload(message);

        final JsonObject result = getServiceRequestAsJson(subject, tenantId, deviceId, payload);
        if (gatewayId != null) {
            result.put(APP_PROPERTY_GATEWAY_ID, gatewayId);
        }
        return result;
    }

    /**
     * Build a JSON object as a reply to a registration request via the vert.x event bus.
     *
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param result The {@link RegistrationResult} object with the payload for the reply object.
     * @return JsonObject The JSON reply object that is to be sent back via the vert.x event bus.
     */
    public static JsonObject getServiceReplyAsJson(final String tenantId, final String deviceId, final RegistrationResult result) {
        return getServiceReplyAsJson(result.getStatus(), tenantId, deviceId, result.getPayload());
    }

    /**
     * Checks if a JSON message contains a given status code.
     *  
     * @param msg The message to check.
     * @param expectedStatus The expected status code.
     * @return {@code true} if the given message has a string typed <em>status</em> property and the property's value
     *                      is the string representation of the expected status.
     * @throws NullPointerException if message is {@code null}.
     */
    public static boolean hasStatus(final JsonObject msg, final int expectedStatus) {

        return Objects.requireNonNull(msg).getInteger(MessageHelper.APP_PROPERTY_STATUS).equals(expectedStatus);
    }
}
