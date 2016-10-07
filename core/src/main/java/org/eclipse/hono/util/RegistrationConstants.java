/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_DEVICE_ID;
import static org.eclipse.hono.util.MessageHelper.APP_PROPERTY_TENANT_ID;
import static org.eclipse.hono.util.MessageHelper.decodeIdFromJson;
import static org.eclipse.hono.util.MessageHelper.getApplicationProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Constants &amp; utility methods used throughout the Registration API.
 */
public final class RegistrationConstants {

    /* registration actions */
    public static final String ACTION_REGISTER   = "register";
    public static final String ACTION_FIND       = "find";
    public static final String ACTION_GET        = "get";
    public static final String ACTION_DEREGISTER = "deregister";
    public static final String ACTION_UPDATE     = "update";


    /* message fields */
    public static final String APP_PROPERTY_CORRELATION_ID       = "correlation-id";
    public static final String APP_PROPERTY_ACTION               = "action";
    public static final String APP_PROPERTY_KEY                  = "key";
    public static final String APP_PROPERTY_STATUS               = "status";
    public static final String FIELD_PAYLOAD                     = "payload";

    public static final String REGISTRATION_ENDPOINT             = "registration";
    public static final String PATH_SEPARATOR                    = "/";
    public static final String NODE_ADDRESS_REGISTRATION_PREFIX  = REGISTRATION_ENDPOINT + PATH_SEPARATOR;

    /**
     * The vert.x event bus address to which inbound registration messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_REGISTRATION_IN = "registration.in";


    private RegistrationConstants() {
        // prevent instantiation
    }

    public static JsonObject getRegistrationMsg(final Message message) {
        final String deviceId = MessageHelper.getDeviceIdAnnotation(message);
        final String tenantId = MessageHelper.getTenantIdAnnotation(message);
        final String key = getKey(message);
        final String action = getAction(message);
        final JsonObject payload = MessageHelper.getJsonPayload(message);
        return getRegistrationJson(action, tenantId, deviceId, key, payload);
    }

    public static JsonObject getReply(final int status, final String tenantId, final String deviceId) {
        return getReply(status, tenantId, deviceId, null);
    }

    public static JsonObject getReply(final String tenantId, final String deviceId, final RegistrationResult result) {
        return getReply(result.getStatus(), tenantId, deviceId, result.getPayload());
    }

    public static JsonObject getReply(final int status, final String tenantId, final String deviceId, final JsonObject payload) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        jsonObject.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        jsonObject.put(APP_PROPERTY_STATUS, Integer.toString(status));
        if (payload != null) {
            jsonObject.put(FIELD_PAYLOAD, payload);
        }
        return jsonObject;
    }

    public static Message getAmqpReply(final io.vertx.core.eventbus.Message<JsonObject> message) {
        final String tenantId = message.body().getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = message.body().getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
        final String status = message.body().getString(RegistrationConstants.APP_PROPERTY_STATUS);
        final JsonObject correlationIdJson = message.body().getJsonObject(RegistrationConstants.APP_PROPERTY_CORRELATION_ID);
        final Object correlationId = decodeIdFromJson(correlationIdJson);
        final boolean isApplCorrelationId = message.body().getBoolean(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID, false);
        return getAmqpReply(status, correlationId, tenantId, deviceId, isApplCorrelationId, message.body().getJsonObject(FIELD_PAYLOAD));
    }

    public static Message getAmqpReply(final String status, final Object correlationId, final String tenantId,
            final String deviceId, final boolean isApplCorrelationId, final JsonObject payload) {

        final ResourceIdentifier address = ResourceIdentifier.from(RegistrationConstants.REGISTRATION_ENDPOINT, tenantId, deviceId);
        final Message message = ProtonHelper.message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setCorrelationId(correlationId);
        message.setAddress(address.toString());

        final Map<String, Object> map = new HashMap<>();
        map.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        map.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        map.put(APP_PROPERTY_STATUS, status);
        message.setApplicationProperties(new ApplicationProperties(map));

        if (isApplCorrelationId) {
            Map<Symbol, Object> annotations = new HashMap<>();
            annotations.put(Symbol.valueOf(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID), isApplCorrelationId);
            message.setMessageAnnotations(new MessageAnnotations(annotations));
        }

        if (payload != null) {
            message.setContentType("application/json; charset=utf-8");
            message.setBody(new Data(new Binary(payload.encode().getBytes())));
        }
        return message;
    }

    public static JsonObject getRegistrationJson(final String action, final String tenantId, final String deviceId) {
        return getRegistrationJson(action, tenantId, deviceId, null);
    }

    public static JsonObject getRegistrationJson(final String action, final String tenantId, final String deviceId, final JsonObject payload) {
        return getRegistrationJson(action, tenantId, deviceId, null, payload);
    }

    public static JsonObject getRegistrationJson(final String action, final String tenantId, final String deviceId, final String key, final JsonObject payload) {
        final JsonObject msg = new JsonObject();
        msg.put(APP_PROPERTY_ACTION, action);
        msg.put(APP_PROPERTY_DEVICE_ID, deviceId);
        msg.put(APP_PROPERTY_TENANT_ID, tenantId);
        if (key != null) {
            msg.put(APP_PROPERTY_KEY, key);
        }
        if (payload != null) {
            msg.put(FIELD_PAYLOAD, payload);
        }
        return msg;
    }

    private static String getAction(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_ACTION, String.class);
    }

    private static String getKey(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_KEY, String.class);
    }
}
