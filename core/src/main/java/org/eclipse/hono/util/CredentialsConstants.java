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
 */package org.eclipse.hono.util;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;

import java.util.*;

import static org.eclipse.hono.util.MessageHelper.*;

/**
 * Constants &amp; utility methods used throughout the Credentials API.
 */

public final class CredentialsConstants {
    /* registration actions */
    public static final String OPERATION_GET                     = "get";
    public static final String OPERATION_ADD                     = "add";
    public static final String OPERATION_UPDATE                  = "update";
    public static final String OPERATION_REMOVE                  = "remove";

    /* message payload fields */
    public static final String FIELD_PAYLOAD                     = "payload";
    public static final String FIELD_ENABLED                     = "enabled";
    public static final String FIELD_TYPE                        = "type";
    public static final String FIELD_AUTH_ID                     = "auth-id";
    public static final String FIELD_DEVICE_ID                   = "device-id";
    public static final String FIELD_SECRETS                     = "secrets";

    /* secrets fields */
    public static final String FIELD_SECRETS_PWD_HASH            = "pwd-hash";
    public static final String FIELD_SECRETS_SALT                = "salt";
    public static final String FIELD_SECRETS_HASH_FUNCTION       = "hash-function";
    public static final String FIELD_SECRETS_NOT_BEFORE          = "not-before";
    public static final String FIELD_SECRETS_NOT_AFTER           = "not-after";

    public static final String CREDENTIALS_ENDPOINT              = "credentials";

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
        return getCredentialsJson(subject,tenantId,payload);
    }

    public static JsonObject getCredentialsJson(final String subject, String tenantId, final JsonObject payload) {
        final JsonObject msg = new JsonObject();
        msg.put(SYS_PROPERTY_SUBJECT, subject);
        msg.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        if (payload != null) {
            msg.put(FIELD_PAYLOAD, payload);
        }
        return msg;
    }

    public static JsonObject getReply(final String tenantId, final String deviceId, final CredentialsResult result) {
        return getReply(result.getStatus(), tenantId, deviceId, result.getPayload());
    }

    public static JsonObject getReply(final int status, final String tenantId, final String deviceId, final JsonObject payload) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        if (deviceId != null) {
            jsonObject.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        }
        jsonObject.put(APP_PROPERTY_STATUS, Integer.toString(status));
        if (payload != null) {
            jsonObject.put(FIELD_PAYLOAD, payload);
        }
        return jsonObject;
    }


    // called from onLinkAttach in sender role
    public static Message getAmqpReply(final io.vertx.core.eventbus.Message<JsonObject> message) {
        final String tenantId = message.body().getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = message.body().getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
        final String status = message.body().getString(APP_PROPERTY_STATUS);
        final JsonObject correlationIdJson = message.body().getJsonObject(APP_PROPERTY_CORRELATION_ID);
        final Object correlationId = decodeIdFromJson(correlationIdJson);
        final boolean isApplCorrelationId = message.body().getBoolean(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID, false);
        return getAmqpReply(status, correlationId, tenantId, deviceId, isApplCorrelationId, message.body().getJsonObject(FIELD_PAYLOAD));
    }

    public static Message getAmqpReply(final String status, final Object correlationId, final String tenantId,
                                       final String deviceId, final boolean isApplCorrelationId, final JsonObject payload) {

        final ResourceIdentifier address = ResourceIdentifier.from(CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId, deviceId);
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
            message.setBody(new AmqpValue(payload.encode()));
        }
        return message;
    }

    public static boolean isValidSubject(final String subject) {
        if (subject == null) {
            return false;
        } else {
            return OPERATIONS.contains(subject);
        }
    }


}
