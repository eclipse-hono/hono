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

import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.impl.ProtonReceiverImpl;
import io.vertx.proton.impl.ProtonSenderImpl;

/**
 * Utility methods for working with Proton {@code Message}s.
 *
 */
public final class MessageHelper {

    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID          = "device_id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID          = "tenant_id";
    /**
     * The name of the AMQP 1.0 message application property containing the resource a message is addressed at.
     */
    public static final String APP_PROPERTY_RESOURCE          = "resource";

    public static final String ANNOTATION_X_OPT_APP_CORRELATION_ID          = "x-opt-app-correlation-id";

    private MessageHelper() {
    }

    public static String getDeviceId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_DEVICE_ID, String.class);
    }

    public static String getTenantId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_TENANT_ID, String.class);
    }

    public static String getDeviceIdAnnotation(final Message msg) {
        Objects.requireNonNull(msg);
        return getAnnotation(msg, APP_PROPERTY_DEVICE_ID, String.class);
    }

    public static String getTenantIdAnnotation(final Message msg) {
        Objects.requireNonNull(msg);
        return getAnnotation(msg, APP_PROPERTY_TENANT_ID, String.class);
    }

    /**
     * Gets the value of the {@code x-opt-appl-correlation-id} annotation from a message.
     * 
     * @param msg the message to get the annotation from.
     * @return the value of the annotation (if present) or {@code false} if the message
     *         does not contain the annotation.
     */
    public static boolean getXOptAppCorrelationId(final Message msg) {
        Objects.requireNonNull(msg);
        Boolean value = getAnnotation(msg, ANNOTATION_X_OPT_APP_CORRELATION_ID, Boolean.class);
        return value == null ? false : value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getApplicationProperty(final ApplicationProperties props, final String name, final Class<T> type) {
        if (props == null) {
            return null;
        } else {
            Object value = props.getValue().get(name);
            if (type.isInstance(value)) {
                return (T) value;
            } else {
                return null;
            }
        }
    }

    /**
     * Parses a message's body into a JSON object.
     * 
     * @param msg The AMQP 1.0 message to parse the body of.
     * @return The message body parsed into a JSON object or {@code null} if the message does not have a
     *         <em>Data</em> or <em>AmqpValue</em> section or the message's content type is not
     *         {@code application/json}.
     * @throws DecodeException if the body of the message cannot be parsed into a JSON object.
     */
    public static JsonObject getJsonPayload(final Message msg) {
        JsonObject result = null;
        if (msg.getBody() != null && msg.getContentType() != null && msg.getContentType().startsWith("application/json")) {
            if (msg.getBody() instanceof Data) {
                Data body = (Data) msg.getBody();
                result = new JsonObject(new String(body.getValue().getArray()));
            } else if (msg.getBody() instanceof AmqpValue) {
                AmqpValue body = (AmqpValue) msg.getBody();
                if (body.getValue() instanceof String) {
                    result = new JsonObject((String) body.getValue());
                }
            }
        }
        return result;
    }

    public static void addTenantId(final Message msg, final String tenantId) {
        addProperty(msg, APP_PROPERTY_TENANT_ID, tenantId);
    }

    public static void addDeviceId(final Message msg, final String deviceId) {
        addProperty(msg, APP_PROPERTY_DEVICE_ID, deviceId);
    }

    @SuppressWarnings("unchecked")
    public static void addProperty(final Message msg, final String key, final Object value) {
        ApplicationProperties props = msg.getApplicationProperties();
        if (props == null) {
            props = new ApplicationProperties(new HashMap<String, Object>());
            msg.setApplicationProperties(props);
        }
        props.getValue().put(key, value);
    }

    public static void rejected(final ProtonDelivery delivery, final String error, final String description) {
        final ErrorCondition errorCondition = ProtonHelper.condition(error, description);
        final Rejected rejected = new Rejected();
        rejected.setError(errorCondition);
        delivery.disposition(rejected, true);
    }

    /**
     * Adds several AMQP 1.0 message <em>annotations</em> to the given message that are used to process/route the message.
     *
     * @param msg the message to add the message annotations to.
     * @param resourceIdentifier the resource identifier that will be added as annotation.
     */
    public static void annotate(final Message msg, final ResourceIdentifier resourceIdentifier) {
        MessageHelper.addAnnotation(msg, APP_PROPERTY_TENANT_ID, resourceIdentifier.getTenantId());
        MessageHelper.addAnnotation(msg, APP_PROPERTY_DEVICE_ID, resourceIdentifier.getResourceId());
        MessageHelper.addAnnotation(msg, APP_PROPERTY_RESOURCE, resourceIdentifier.toString());
    }

    /**
     * Adds a value for a symbol to an AMQP 1.0 message's <em>annotations</em>.
     * 
     * @param msg the message to add the symbol to.
     * @param key the name of the symbol to add a value for.
     * @param value the value to add.
     */
    public static void addAnnotation(final Message msg, final String key, final Object value) {
        MessageAnnotations annotations = msg.getMessageAnnotations();
        if (annotations == null) {
            annotations = new MessageAnnotations(new HashMap<>());
            msg.setMessageAnnotations(annotations);
        }
        annotations.getValue().put(Symbol.getSymbol(key), value);
    }

    /**
     * Returns the value to which the specified key is mapped in the message annotations,
     * or {@code null} if the message annotations contain no mapping for the key.
     *
     * @param <T> the expected type of the property to read.
     * @param msg the message that contains the annotations.
     * @param key the name of the symbol to return a value for.
     * @param type the expected type of the value.
     * @return the annotation's value or {@code null} if no such annotation exists or its value
     *          is not of the expected type.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAnnotation(final Message msg, final String key, final Class<T> type) {
        MessageAnnotations annotations = msg.getMessageAnnotations();
        if (annotations == null) {
            return null;
        } else {
            Object value = annotations.getValue().get(Symbol.getSymbol(key));
            if (type.isInstance(value)) {
                return (T) value;
            } else {
                return null;
            }
        }
    }

    public static String getLinkName(final ProtonLink<?> link) {
        if (link instanceof ProtonReceiverImpl) {
            return ((ProtonReceiverImpl) link).getName();
        } else if (link instanceof ProtonSenderImpl) {
            return ((ProtonSenderImpl) link).getName();
        } else {
            return "unknown";
        }
    }

    /**
     * Encodes the given ID object to JSON representation. Supported types for AMQP 1.0 correlation/messageIds are
     * String, UnsignedLong, UUID and Binary.
     * @param id the id to encode to JSON
     * @return a JsonObject containing the JSON represenatation
     * @throws IllegalArgumentException if the type is not supported
     */
    public static JsonObject encodeIdToJson(final Object id) {

        final JsonObject json = new JsonObject();
        if (id instanceof String) {
            json.put("type", "string");
            json.put("id", id);
        } else if (id instanceof UnsignedLong) {
            json.put("type", "ulong");
            json.put("id", id.toString());
        } else if (id instanceof UUID) {
            json.put("type", "uuid");
            json.put("id", id.toString());
        } else if (id instanceof Binary) {
            json.put("type", "binary");
            final Binary binary = (Binary) id;
            json.put("id", Base64.getEncoder().encodeToString(binary.getArray()));
        } else {
            throw new IllegalArgumentException("type " + id.getClass().getName() + " is not supported");
        }
        return json;
    }

    /**
     * Decodes the given JsonObject to JSON representation.
     * Supported types for AMQP 1.0 correlation/messageIds are String, UnsignedLong, UUID and Binary.
     * @param json JSON representation of an ID
     * @return an ID object of correct type
     */
    public static Object decodeIdFromJson(final JsonObject json)
    {
        final String type = json.getString("type");
        final String id = json.getString("id");
        switch (type) {
            case "string":
                return id;
            case "ulong":
                return UnsignedLong.valueOf(id);
            case "uuid":
                return UUID.fromString(id);
            case "binary":
                return new Binary(Base64.getDecoder().decode(id));
            default:
                throw new IllegalArgumentException("type " + type + " is not supported");
        }
    }
}
