/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import java.nio.charset.StandardCharsets;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;

/**
 * Utility methods for working with Proton {@code Message}s.
 *
 */
public final class MessageHelper {

    /**
     * The name of the AMQP 1.0 message annotation that is used to indicate the type of
     * the <em>correlation-id</em>.
     */
    public static final String ANNOTATION_X_OPT_APP_CORRELATION_ID = "x-opt-app-correlation-id";

    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID              = "device_id";
    /**
     * The name of the AMQP 1.0 application property that is used to convey the
     * address that a message has been originally published to by a device.
     */
    public static final String APP_PROPERTY_ORIG_ADDRESS           = "orig_address";
    /**
     * The name of the AMQP 1.0 message application property containing the name of the
     * protocol adapter over which an uploaded message has originally been received.
     */
    public static final String APP_PROPERTY_ORIG_ADAPTER           = "orig_adapter";
    /**
     * The name of the AMQP 1.0 message application property containing a JWT token asserting a device's registration status.
     */
    public static final String APP_PROPERTY_REGISTRATION_ASSERTION = "reg_assertion";
    /**
     * The name of the AMQP 1.0 message application property containing the resource a message is addressed at.
     */
    public static final String APP_PROPERTY_RESOURCE               = "resource";
    /**
     * The name of the AMQP 1.0 message application property containing the status code indicating the outcome of processing a request.
     */
    public static final String APP_PROPERTY_STATUS                 = "status";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID              = "tenant_id";

    /**
     * The AMQP 1.0 <em>absolute-expiry-time</em> message property.
     */
    public static final String SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME   = "absolute-expiry-time";
    /**
     * The AMQP 1.0 <em>content-encoding</em> message property.
     */
    public static final String SYS_PROPERTY_CONTENT_ENCODING       = "content-encoding";
    /**
     * The AMQP 1.0 <em>content-type</em> message property.
     */
    public static final String SYS_PROPERTY_CONTENT_TYPE           = "content-type";
    /**
     * The AMQP 1.0 <em>correlation-id</em> message property.
     */
    public static final String SYS_PROPERTY_CORRELATION_ID         = "correlation-id";
    /**
     * The AMQP 1.0 <em>creation-time</em> message property.
     */
    public static final String SYS_PROPERTY_CREATION_TIME          = "creation-time";
    /**
     * The AMQP 1.0 <em>group-id</em> message property.
     */
    public static final String SYS_PROPERTY_GROUP_ID               = "group-id";
    /**
     * The AMQP 1.0 <em>group-sequence</em> message property.
     */
    public static final String SYS_PROPERTY_GROUP_SEQUENCE         = "group-sequence";
    /**
     * The AMQP 1.0 <em>message-id</em> message property.
     */
    public static final String SYS_PROPERTY_MESSAGE_ID             = "message-id";
    /**
     * The AMQP 1.0 <em>reply-to</em> message property.
     */
    public static final String SYS_PROPERTY_REPLY_TO               = "reply-to";
    /**
     * The AMQP 1.0 <em>reply-to-group-id</em> message property.
     */
    public static final String SYS_PROPERTY_REPLY_TO_GROUP_ID      = "reply-to-group-id";
    /**
     * The AMQP 1.0 <em>subject</em> message property.
     */
    public static final String SYS_PROPERTY_SUBJECT                = "subject";
    /**
     * The AMQP 1.0 <em>user-id</em> message property.
     */
    public static final String SYS_PROPERTY_USER_ID                = "user-id";
    /**
     * The AMQP 1.0 <em>to</em> message property.
     */
    public static final String SYS_PROPERTY_TO                     = "to";

    /**
     * The {@code JMS_AMQP_CONTENT_ENCODING} vendor property name.
     */
    public static final String JMS_VENDOR_PROPERTY_CONTENT_ENCODING = "JMS_AMQP_CONTENT_ENCODING";
    /**
     * The {@code JMS_AMQP_CONTENT_TYPE} vendor property name.
     */
    public static final String JMS_VENDOR_PROPERTY_CONTENT_TYPE     = "JMS_AMQP_CONTENT_TYPE";

    private static final Logger LOG = LoggerFactory.getLogger(MessageHelper.class);

    private MessageHelper() {
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_DEVICE_ID} application property.
     * 
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     */
    public static String getDeviceId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_DEVICE_ID, String.class);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_TENANT_ID} application property.
     * 
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     */
    public static String getTenantId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_TENANT_ID, String.class);
    }

    /**
     * Gets the registration assertion conveyed in an AMQP 1.0 message.
     * <p>
     * The assertion is expected to be contained in the messages's <em>delivery-annotation</em>
     * under key {@link #APP_PROPERTY_REGISTRATION_ASSERTION}.
     * 
     * @param msg The message.
     * @return The assertion or {@code null} if the message does not contain an assertion (at the
     *         expected location).
     */
    public static String getRegistrationAssertion(final Message msg) {
        return getRegistrationAssertion(msg, false);
    }

    /**
     * Gets and removes the registration assertion conveyed in an AMQP 1.0 message.
     * <p>
     * The assertion is expected to be contained in the messages's <em>delivery-annotation</em>
     * under key {@link #APP_PROPERTY_REGISTRATION_ASSERTION}.
     * 
     * @param msg The message.
     * @return The assertion or {@code null} if the message does not contain an assertion (at the
     *         expected location).
     */
    public static String getAndRemoveRegistrationAssertion(final Message msg) {
        return getRegistrationAssertion(msg, true);
    }

    private static String getRegistrationAssertion(final Message msg, final boolean removeAssertion) {
        Objects.requireNonNull(msg);
        String assertion = null;
        ApplicationProperties properties = msg.getApplicationProperties();
        if (properties != null) {
            Object obj = null;
            if (removeAssertion) {
                obj = properties.getValue().remove(APP_PROPERTY_REGISTRATION_ASSERTION);
            } else {
                obj = properties.getValue().get(APP_PROPERTY_REGISTRATION_ASSERTION);
            }
            if (obj instanceof String) {
                assertion = (String) obj;
            }
        }
        return assertion;
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_DEVICE_ID} annotation.
     * 
     * @param msg The message.
     * @return The annotation value or {@code null} if not set.
     */
    public static String getDeviceIdAnnotation(final Message msg) {
        Objects.requireNonNull(msg);
        return getAnnotation(msg, APP_PROPERTY_DEVICE_ID, String.class);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_TENANT_ID} annotation.
     * 
     * @param msg The message.
     * @return The annotation value or {@code null} if not set.
     */
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

    /**
     * Gets the value of a specific <em>application property</em>.
     * 
     * @param <T> The expected type of the property to retrieve the value of.
     * @param props The application properties to retrieve the value from.
     * @param name The property name.
     * @param type The expected value type.
     * @return The value or {@code null} if the properties do not contain a value of
     *         the expected type for the given name.
     */
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
     *         <em>Data</em> nor an <em>AmqpValue</em> section.
     * @throws NullPointerException if the message is {@code null}.
     * @throws DecodeException if the payload cannot be parsed into a JSON object.
     */
    public static JsonObject getJsonPayload(final Message msg) {

        final String payload = getPayload(msg);
        return (payload != null ? new JsonObject(payload) : null);
    }

    /**
     * Gets a message's body as String object that can be used for constructing a JsonObject or bind a POJO using
     * jackson-databind e.g.
     *
     * @param msg The AMQP 1.0 message to parse the body of.
     * @return The message body parsed into a JSON object or {@code null} if the message does not have a <em>Data</em>
     *         nor an <em>AmqpValue</em> section.
     * @throws NullPointerException if the message is {@code null}.
     */
    public static String getPayload(final Message msg) {

        Objects.requireNonNull(msg);
        if (msg.getBody() == null) {
            LOG.debug("message has no body");
            return null;
        }

        if (msg.getBody() instanceof Data) {
            Data body = (Data) msg.getBody();
            return new String(body.getValue().getArray(), StandardCharsets.UTF_8);
        } else if (msg.getBody() instanceof AmqpValue) {
            AmqpValue body = (AmqpValue) msg.getBody();
            if (body.getValue() instanceof String) {
                return (String) body.getValue();
            }
        }

        LOG.debug("unsupported body type [{}]", msg.getBody().getClass().getName());
        return null;
    }

    /**
     * Adds a tenant ID to a message's <em>application properties</em>.
     * <p>
     * The name of the application property is {@link #APP_PROPERTY_TENANT_ID}.
     * 
     * @param msg The message.
     * @param tenantId The tenant identifier to add.
     */
    public static void addTenantId(final Message msg, final String tenantId) {
        addProperty(msg, APP_PROPERTY_TENANT_ID, tenantId);
    }

    /**
     * Adds a device ID to a message's <em>application properties</em>.
     * <p>
     * The name of the application property is {@link #APP_PROPERTY_DEVICE_ID}.
     * 
     * @param msg The message.
     * @param deviceId The device identifier to add.
     */
    public static void addDeviceId(final Message msg, final String deviceId) {
        addProperty(msg, APP_PROPERTY_DEVICE_ID, deviceId);
    }

    /**
     * Adds a registration assertion to an AMQP 1.0 message.
     * <p>
     * The assertion is put to the message's <em>application-properties</em> under key
     * {@link #APP_PROPERTY_REGISTRATION_ASSERTION}.
     * 
     * @param msg The message.
     * @param token The assertion to add.
     */
    public static void addRegistrationAssertion(final Message msg, final String token) {
        addProperty(msg, APP_PROPERTY_REGISTRATION_ASSERTION, token);
    }

    /**
     * Adds a property to an AMQP 1.0 message.
     * <p>
     * The property is added to the message's <em>application-properties</em>.
     * 
     * @param msg The message.
     * @param key The property key.
     * @param value The property value.
     */
    @SuppressWarnings("unchecked")
    public static void addProperty(final Message msg, final String key, final Object value) {
        ApplicationProperties props = msg.getApplicationProperties();
        if (props == null) {
            props = new ApplicationProperties(new HashMap<String, Object>());
            msg.setApplicationProperties(props);
        }
        props.getValue().put(key, value);
    }

    /**
     * Sets an AMQP 1.0 message's delivery state to <em>rejected</em>.
     * 
     * @param delivery The message's delivery object.
     * @param error The error condition to set as the reason for rejecting the message.
     */
    public static void rejected(final ProtonDelivery delivery, final ErrorCondition error) {
        final Rejected rejected = new Rejected();
        rejected.setError(error);
        delivery.disposition(rejected, true);
    }

    /**
     * Adds several AMQP 1.0 message <em>annotations</em> to the given message that are used to process/route the message.
     * <p>
     * In particular, the following annotations are added:
     * <ul>
     * <li>{@link #APP_PROPERTY_TENANT_ID} - the tenant ID segment of the resource identifier</li>
     * <li>{@link #APP_PROPERTY_DEVICE_ID} - the resource ID segment of the resource identifier (if not {@code null}</li>
     * <li>{@link #APP_PROPERTY_RESOURCE} - the full resource path including the endpoint, the tenant and the resource ID</li>
     * </ul>
     *
     * @param msg the message to add the message annotations to.
     * @param resourceIdentifier the resource identifier that will be added as annotation.
     */
    public static void annotate(final Message msg, final ResourceIdentifier resourceIdentifier) {
        MessageHelper.addAnnotation(msg, APP_PROPERTY_TENANT_ID, resourceIdentifier.getTenantId());
        if (resourceIdentifier.getResourceId() != null) {
            MessageHelper.addAnnotation(msg, APP_PROPERTY_DEVICE_ID, resourceIdentifier.getResourceId());
        }
        MessageHelper.addAnnotation(msg, APP_PROPERTY_RESOURCE, resourceIdentifier.toString());
    }

    /**
     * Adds JMS vendor properties defined by
     * <a href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     * AMQP JMS Mapping 1.0</a> as AMQP 1.0 application properties to a given message.
     * <p>
     * The following vendor properties are added (if the message has a corresponding
     * non-null value set):
     * <ul>
     * <li>{@link #JMS_VENDOR_PROPERTY_CONTENT_TYPE}</li>
     * <li>{@link #JMS_VENDOR_PROPERTY_CONTENT_ENCODING}</li>
     * </ul>
     *
     * @param msg the message to add the vendor properties to.
     */
    public static void addJmsVendorProperties(final Message msg) {
        if (!StringUtils.isEmpty(msg.getContentType())) {
            MessageHelper.addProperty(msg, JMS_VENDOR_PROPERTY_CONTENT_TYPE, msg.getContentType());
        }
        if (!StringUtils.isEmpty(msg.getContentEncoding())) {
            MessageHelper.addProperty(msg, JMS_VENDOR_PROPERTY_CONTENT_ENCODING, msg.getContentEncoding());
        }
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

    /**
     * Encodes the given ID object to JSON representation. Supported types for AMQP 1.0 correlation/messageIds are
     * String, UnsignedLong, UUID and Binary.
     * 
     * @param id The identifier to encode to JSON
     * @return A JsonObject containing the JSON representation of the identifier.
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
