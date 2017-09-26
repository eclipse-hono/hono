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
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.hono.util.MessageHelper;

/**
 * Constants &amp; utility methods that are common to APIs that follow the request response pattern.
 */
public class RequestResponseApiConstants {

    /* message payload fields */
    public static final String FIELD_PAYLOAD                     = "payload";
    public static final String FIELD_ENABLED                     = "enabled";
    public static final String FIELD_DEVICE_ID                   = "device-id";
    public static final String FIELD_TENANT_ID                   = "tenant-id";

    /* message property names */
    public static final String APP_PROPERTY_KEY                  = "key";

    /**
     * Build a Proton message as a reply for an endpoint from the json payload that e.g. is received from the vert.x eventbus
     * from the implementing service.
     *
     * @param endpoint The endpoint the reply message will be built for.
     * @param payload The json payload received.
     * @return Message The built Proton message.
     */
    public static final Message getAmqpReply(final String endpoint, final JsonObject payload) {
        final String tenantId = payload.getString(FIELD_TENANT_ID);
        final String deviceId = payload.getString(FIELD_DEVICE_ID);
        final String status = payload.getString(MessageHelper.APP_PROPERTY_STATUS);
        final JsonObject correlationIdJson = payload.getJsonObject(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
        final Object correlationId = MessageHelper.decodeIdFromJson(correlationIdJson);
        final boolean isApplCorrelationId = payload.getBoolean(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID, false);
        return getAmqpReply(endpoint, status, correlationId, tenantId, deviceId, isApplCorrelationId,
                payload.getJsonObject(CredentialsConstants.FIELD_PAYLOAD));
    }

    /**
     * Build a Proton message containing the reply to a request for the provided endpoint.
     *
     * @param endpoint The endpoint the reply message will be built for.
     * @param status The status from the service that processed the message.
     * @param correlationId The UUID to correlate the reply with the originally sent message.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param isApplCorrelationId Flag to inidicate if the correlationId has to be available as application property
     *        {@link MessageHelper#ANNOTATION_X_OPT_APP_CORRELATION_ID}.
     * @param payload The payload of the message reply as json object.
     * @return Message The built Proton message. Maybe null. In that case, the message reply will not contain a body.
     */
    public static final Message getAmqpReply(final String endpoint, final String status, final Object correlationId,
                                             final String tenantId, final String deviceId, final boolean isApplCorrelationId,
                                             final JsonObject payload) {

        final ResourceIdentifier address = ResourceIdentifier.from(endpoint, tenantId, deviceId);
        final Message message = ProtonHelper.message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setCorrelationId(correlationId);
        message.setAddress(address.toString());

        final Map<String, Object> map = new HashMap<>();
        map.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        map.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        map.put(MessageHelper.APP_PROPERTY_STATUS, status);
        message.setApplicationProperties(new ApplicationProperties(map));

        if (isApplCorrelationId) {
            Map<Symbol, Object> annotations = new HashMap<>();
            annotations.put(Symbol.valueOf(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID), true);
            message.setMessageAnnotations(new MessageAnnotations(annotations));
        }

        if (payload != null) {
            message.setContentType("application/json; charset=utf-8");
            message.setBody(new AmqpValue(payload.encode()));
        }
        return message;
    }

    /**
     * Build a Json object as a reply for internal communication via the vert.x event bus.
     * Service implementations may use this method to build their response when replying to a request that was received for processing.
     *
     * @param status The status from the service that processed the message.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @return JsonObject The json reply object that is to be sent back via the vert.x event bus.
     */
    public static final JsonObject getServiceReplyAsJson(final int status, final String tenantId, final String deviceId) {
        return getServiceReplyAsJson(status, tenantId, deviceId, null);
    }

    /**
     * Build a Json object as a reply for internal communication via the vert.x event bus.
     * Services use this object to build their response when replying to a request that was received for processing.
     *
     * @param status The status from the service that processed the message.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param payload The payload of the message reply as json object.
     * @return JsonObject The json reply object that is to be sent back via the vert.x event bus.
     */
    public static final JsonObject getServiceReplyAsJson(final int status, final String tenantId, final String deviceId,
                                                         final JsonObject payload) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(FIELD_TENANT_ID, tenantId);
        if (deviceId != null) {
            jsonObject.put(FIELD_DEVICE_ID, deviceId);
        }
        jsonObject.put(MessageHelper.APP_PROPERTY_STATUS, Integer.toString(status));
        if (payload != null) {
            jsonObject.put(FIELD_PAYLOAD, payload);
        }
        return jsonObject;
    }

    /**
     * Build a Json object as a reply for internal communication via the vert.x event bus.
     * Services use this object to build their response when replying to a request that was received for processing.
     *
     * @param operation The operation that shall be processed by the service.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @return JsonObject The json object for the request that is to be sent via the vert.x event bus.
     */
    public static final JsonObject getServiceRequestAsJson(final String operation, final String tenantId, final String deviceId) {
        return getServiceRequestAsJson(operation, tenantId, deviceId, null);
    }

    /**
     * Build a Json object as a reply for internal communication via the vert.x event bus.
     * Services use this object to build their response when replying to a request that was received for processing.
     *
     * @param operation The operation that shall be processed by the service.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to. Maybe null - then no deviceId will be contained.
     * @param payload The payload from the request that is passed to the processing service.
     * @return JsonObject The json object for the request that is to be sent via the vert.x event bus.
     */
    public static final JsonObject getServiceRequestAsJson(final String operation, final String tenantId, final String deviceId,
                                                           final JsonObject payload) {
        return getServiceRequestAsJson(operation, tenantId, deviceId, null, payload);
    }

    /**
     * Build a Json object as a reply for internal communication via the vert.x event bus.
     * Services use this object to build their response when replying to a request that was received for processing.
     *
     * @param operation The operation that shall be processed by the service.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to. Maybe null - then no deviceId will be contained.
     * @param valueForKeyProperty The value for the application property {@link #APP_PROPERTY_KEY}.
     *                            If null, the key will not be set.
     * @param payload The payload from the request that is passed to the processing service.
     * @return JsonObject The json object for the request that is to be sent via the vert.x event bus.
     */
    public static final JsonObject getServiceRequestAsJson(final String operation, final String tenantId, final String deviceId,
                                                           final String valueForKeyProperty, final JsonObject payload) {
        final JsonObject msg = new JsonObject();
        msg.put(MessageHelper.SYS_PROPERTY_SUBJECT, operation);
        if (deviceId != null) {
            msg.put(FIELD_DEVICE_ID, deviceId);
        }
        msg.put(FIELD_TENANT_ID, tenantId);
        if (valueForKeyProperty != null) {
            msg.put(RegistrationConstants.APP_PROPERTY_KEY, valueForKeyProperty);
        }
        if (payload != null) {
            msg.put(RegistrationConstants.FIELD_PAYLOAD, payload);
        }
        return msg;
    }
}
