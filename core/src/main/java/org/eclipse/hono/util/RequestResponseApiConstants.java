/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;

/**
 * Constants &amp; utility methods that are common to APIs that follow the request response pattern.
 */
public abstract class RequestResponseApiConstants {

    /**
     * The MIME type representing the String representation of a JSON Object.
     */
    public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    /* message payload fields */
    public static final String FIELD_PAYLOAD_DEVICE_ID = Constants.JSON_FIELD_DEVICE_ID;
    /**
     * The name of the property that contains the <em>subject DN</em> of the CA certificate
     * that has been configured for a tenant. The subject DN is serialized as defined by
     * <a href="https://tools.ietf.org/html/rfc2253#section-2">RFC 2253, Section 2</a>.
     */
    public static final String FIELD_PAYLOAD_SUBJECT_DN = "subject-dn";
    public static final String FIELD_PAYLOAD_TENANT_ID = Constants.JSON_FIELD_TENANT_ID;

    public static final String FIELD_ENABLED   = "enabled";
    public static final String FIELD_ERROR     = "error";
    public static final String FIELD_PAYLOAD   = "payload";

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
     */
    public static final Message getAmqpReply(final String endpoint, final EventBusMessage response) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(response);

        final Object correlationId = response.getCorrelationId();

        if (correlationId == null) {
            throw new IllegalArgumentException("response must contain correlation ID");
        } else {
            final String tenantId = response.getTenant();
            final String deviceId = response.getDeviceId();
            final Integer status = response.getStatus();
            final boolean isApplCorrelationId = response.isAppCorrelationId();
            final String cacheDirective = response.getCacheDirective();
            final JsonObject payload = response.getJsonPayload();
            final ResourceIdentifier address = ResourceIdentifier.from(endpoint, tenantId, deviceId);

            final Message message = ProtonHelper.message();
            message.setMessageId(UUID.randomUUID().toString());
            message.setCorrelationId(correlationId);
            message.setAddress(address.toString());

            final Map<String, Object> map = new HashMap<>();
            map.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
            map.put(MessageHelper.APP_PROPERTY_STATUS, status);
            if (deviceId != null) {
                map.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
            }
            if (cacheDirective != null) {
                map.put(MessageHelper.APP_PROPERTY_CACHE_CONTROL, cacheDirective);
            }
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
    }
}
