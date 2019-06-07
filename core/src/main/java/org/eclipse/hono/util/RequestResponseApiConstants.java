/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Constants &amp; utility methods that are common to APIs that follow the request response pattern.
 */
public abstract class RequestResponseApiConstants {

    /**
     * The MIME type representing the String representation of a JSON Object.
     */
    public static final String CONTENT_TYPE_APPLICATION_JSON = MessageHelper.CONTENT_TYPE_APPLICATION_JSON;

    /**
     * The name of the property which contains default properties that protocol adapters
     * should add to messages published by a device.
     */
    public static final String FIELD_PAYLOAD_DEFAULTS  = "defaults";
    /**
     * The name of the property that contains the identifier of a device.
     */
    public static final String FIELD_PAYLOAD_DEVICE_ID = Constants.JSON_FIELD_DEVICE_ID;
    /**
     * The name of the property that contains the <em>subject DN</em> of the CA certificate
     * that has been configured for a tenant. The subject DN is serialized as defined by
     * <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     */
    public static final String FIELD_PAYLOAD_SUBJECT_DN = "subject-dn";
    /**
     * The name of the property that contains the identifier of a tenant.
     */
    public static final String FIELD_PAYLOAD_TENANT_ID = Constants.JSON_FIELD_TENANT_ID;

    /**
     * The name of the field that contains a boolean indicating the status of an entity.
     */
    public static final String FIELD_ENABLED   = "enabled";
    /**
     * The name of the field that contains additional information about an error
     * that has occurred while processing a request message.
     */
    public static final String FIELD_ERROR     = "error";
    /**
     * The name of the field that contains the payload of a request or response message.
     */
    public static final String FIELD_PAYLOAD   = "payload";
    /**
     * The name of the field that contains the identifier of the object.
     */
    public static final String FIELD_OBJECT_ID = "id";

    /**
     * Empty default constructor.
     */
    protected RequestResponseApiConstants() {
    }

    /**
     * Creates an AMQP message from a response to a service invocation.
     *
     * @param endpoint The service endpoint that the operation has been invoked on.
     * @param response The response message.
     * @return The AMQP message.
     * @throws NullPointerException if endpoint is {@code null}.
     * @throws IllegalArgumentException if the response does not contain a correlation ID.
     */
    public static final Message getAmqpReply(final String endpoint, final EventBusMessage response) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(response);

        final Object correlationId = response.getCorrelationId();

        if (correlationId == null) {
            throw new IllegalArgumentException("response must contain correlation ID");
        }

        final String tenantId = response.getTenant();
        final String deviceId = response.getDeviceId();
        final Integer status = response.getStatus();
        final String cacheDirective = response.getCacheDirective();
        final JsonObject payload = response.getJsonPayload();
        final ResourceIdentifier address = ResourceIdentifier.from(endpoint, tenantId, deviceId);

        final Message message = ProtonHelper.message();
        message.setMessageId(UUID.randomUUID().toString());
        message.setCorrelationId(correlationId);
        message.setAddress(address.toString());

        final Map<String, Object> map = new HashMap<>();
        map.put(MessageHelper.APP_PROPERTY_STATUS, status);
        if (tenantId != null) {
            map.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        }
        if (deviceId != null) {
            map.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        }
        if (cacheDirective != null) {
            map.put(MessageHelper.APP_PROPERTY_CACHE_CONTROL, cacheDirective);
        }
        message.setApplicationProperties(new ApplicationProperties(map));

        MessageHelper.setJsonPayload(message, payload);

        return message;
    }

    /**
     * Creates an AMQP (response) message for conveying an erroneous outcome of an operation.
     * 
     * @param status The status code.
     * @param errorDescription An (optional) error description which will be put to a <em>Data</em>
     *                         section.
     * @param requestMessage The request message.
     * @return The response message.
     */
    public static final Message getErrorMessage(
            final int status,
            final String errorDescription,
            final Message requestMessage) {

        Objects.requireNonNull(requestMessage);
        if (status < 100 || status >= 600) {
            throw new IllegalArgumentException("illegal status code");
        }

        final Message message = ProtonHelper.message();
        MessageHelper.addStatus(message, status);
        message.setCorrelationId(MessageHelper.getCorrelationId(requestMessage));
        if (errorDescription != null) {
            MessageHelper.setPayload(message, MessageHelper.CONTENT_TYPE_TEXT_PLAIN, Buffer.buffer(errorDescription));
        }
        return message;
    }
}
