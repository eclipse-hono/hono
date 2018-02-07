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

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.hono.util.MessageHelper;

/**
 * Constants &amp; utility methods that are common to APIs that follow the request response pattern.
 */
public class RequestResponseApiConstants {

    /**
     * The MIME type representing the String representation of a JSON Object.
     */
    public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    /* message payload fields */
    public static final String FIELD_DEVICE_ID = "device-id";
    public static final String FIELD_ENABLED   = "enabled";
    public static final String FIELD_ERROR     = "error";
    public static final String FIELD_PAYLOAD   = "payload";
    public static final String FIELD_TENANT_ID = "tenant-id";

    /**
     * Creates an AMQP message from a JSON message containing the response to an
     * invocation of a service operation.
     *
     * @param endpoint The service endpoint that the operation has been invoked on.
     * @param response The JSON message containing the response.
     * @return The AMQP message.
     * @throws NullPointerException if endpoint is {@code null}.
     */
    public static final Message getAmqpReply(final String endpoint, final JsonObject response) {

        final String tenantId = response.getString(FIELD_TENANT_ID);
        final String deviceId = response.getString(FIELD_DEVICE_ID);
        final Integer status = response.getInteger(MessageHelper.APP_PROPERTY_STATUS);
        final JsonObject correlationIdJson = response.getJsonObject(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
        final Object correlationId = MessageHelper.decodeIdFromJson(correlationIdJson);
        final boolean isApplCorrelationId = response.getBoolean(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID, false);
        return getAmqpReply(endpoint, status, correlationId, tenantId, deviceId, isApplCorrelationId,
                response.getJsonObject(CredentialsConstants.FIELD_PAYLOAD));
    }

    /**
     * Creates an AMQP message for a response to an invocation of a service invocation.
     *
     * @param endpoint The service endpoint that the operation has been invoked on.
     * @param status The status from the service that processed the message.
     * @param correlationId The UUID to correlate the reply with the originally sent message.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param isApplCorrelationId Flag to indicate if the correlationId has to be available as application property
     *        {@link MessageHelper#ANNOTATION_X_OPT_APP_CORRELATION_ID}.
     * @param payload The payload of the message reply as JSON object.
     * @return The AMQP message.
     * @throws NullPointerException if any of endpoint, correlation ID or tenant ID is {@code null}.
     */
    public static final Message getAmqpReply(final String endpoint, final Integer status, final Object correlationId,
                                             final String tenantId, final String deviceId, final boolean isApplCorrelationId,
                                             final JsonObject payload) {

        Objects.requireNonNull(correlationId);
        final ResourceIdentifier address = ResourceIdentifier.from(endpoint, tenantId, deviceId);

        final Message message = ProtonHelper.message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setCorrelationId(correlationId);
        message.setAddress(address.toString());

        final Map<String, Object> map = new HashMap<>();
        if (deviceId != null) {
            map.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        }
        map.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        map.put(MessageHelper.APP_PROPERTY_STATUS, status);
        message.setApplicationProperties(new ApplicationProperties(map));

        if (isApplCorrelationId) {
            final Map<Symbol, Object> annotations = new HashMap<>();
            annotations.put(Symbol.valueOf(MessageHelper.ANNOTATION_X_OPT_APP_CORRELATION_ID), true);
            message.setMessageAnnotations(new MessageAnnotations(annotations));
        }

        if (payload != null) {
            message.setContentType(CONTENT_TYPE_APPLICATION_JSON);
            message.setBody(new AmqpValue(payload.encode()));
        }
        return message;
    }

    /**
     * Builds a JSON object as a reply for internal communication via the vert.x event bus.
     * Service implementations may use this method to build their response when replying to a request that was received for processing.
     *
     * @param status The status from the service that processed the message.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @return JsonObject The JSON reply object that is to be sent back via the vert.x event bus.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    public static final JsonObject getServiceReplyAsJson(final int status, final String tenantId, final String deviceId) {
        return getServiceReplyAsJson(status, tenantId, deviceId, null);
    }

    /**
     * Builds a JSON object as a reply for internal communication via the vert.x event bus.
     * Services use this object to build their response when replying to a request that was received for processing.
     *
     * @param status The status from the service that processed the message.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @param payload The payload of the message reply as JSON object.
     * @return JsonObject The JSON reply object that is to be sent back via the vert.x event bus.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    public static final JsonObject getServiceReplyAsJson(final int status, final String tenantId, final String deviceId,
                                                         final JsonObject payload) {

        Objects.requireNonNull(tenantId);

        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(FIELD_TENANT_ID, tenantId);
        jsonObject.put(MessageHelper.APP_PROPERTY_STATUS, status);
        if (deviceId != null) {
            jsonObject.put(FIELD_DEVICE_ID, deviceId);
        }
        if (payload != null) {
            jsonObject.put(FIELD_PAYLOAD, payload);
        }
        return jsonObject;
    }

    /**
     * Builds a JSON object as a request for internal communication via the vert.x event bus.
     * Clients use this object to build their request that is sent to the processing service.
     *
     * @param operation The operation that shall be processed by the service.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to.
     * @return JsonObject The JSON object for the request that is to be sent via the vert.x event bus.
     * @throws NullPointerException if operation or tenant ID are {@code null}.
     */
    public static final JsonObject getServiceRequestAsJson(final String operation, final String tenantId, final String deviceId) {
        return getServiceRequestAsJson(operation, tenantId, deviceId, null);
    }

    /**
     * Builds a JSON object as a request for internal communication via the vert.x event bus.
     * Clients use this object to build their request that is sent to the processing service.
     *
     * @param operation The operation that shall be processed by the service.
     * @param tenantId The tenant for which the message was processed.
     * @param deviceId The device that the message relates to. Maybe null - then no deviceId will be contained.
     * @param payload The payload from the request that is passed to the processing service.
     * @return JsonObject The JSON object for the request that is to be sent via the vert.x event bus.
     * @throws NullPointerException if operation or tenant ID are {@code null}.
     */
    public static final JsonObject getServiceRequestAsJson(final String operation, final String tenantId, final String deviceId,
                                                           final JsonObject payload) {

        Objects.requireNonNull(operation);
        Objects.requireNonNull(tenantId);

        final JsonObject msg = new JsonObject();
        msg.put(MessageHelper.SYS_PROPERTY_SUBJECT, operation);
        msg.put(FIELD_TENANT_ID, tenantId);
        if (deviceId != null) {
            msg.put(FIELD_DEVICE_ID, deviceId);
        }
        if (payload != null) {
            msg.put(RegistrationConstants.FIELD_PAYLOAD, payload);
        }
        return msg;
    }
}
