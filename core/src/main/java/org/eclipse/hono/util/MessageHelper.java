/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * Utility methods for working with Proton {@code Message}s.
 *
 */
public final class MessageHelper {

    /**
     * The name of the AMQP 1.0 message annotation that is used to indicate that the sender of the
     * message intended the message to be <em>retained</em> in the MQTT sense. The value of the
     * property is a boolean. If a message does not contain this annotation, then a consumer must process
     * the message as if the annotation exists and has a value of {@code false}.
     */
    public static final String ANNOTATION_X_OPT_RETAIN = "x-opt-retain";

    /**
     * The name of the AMQP 1.0 message application property containing the caching directive to follow for the body of
     * the message.
     */
    public static final String APP_PROPERTY_CACHE_CONTROL = "cache_control";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the device that has reported the data
     * belongs to.
     */
    public static final String APP_PROPERTY_DEVICE_ID = "device_id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the gateway that wants to report data
     * on behalf of another device.
     */
    public static final String APP_PROPERTY_GATEWAY_ID = "gateway_id";
    /**
     * The name of the AMQP 1.0 message application property containing the flag denoting an event sent whenever
     * a device was auto-provisioned.
     */
    public static final String APP_PROPERTY_REGISTRATION_STATUS = "hono_registration_status";
    /**
     * The name of the AMQP 1.0 message application property containing a lifespan value in seconds.
     */
    public static final String APP_PROPERTY_LIFESPAN = "lifespan";
    /**
     * The name of the AMQP 1.0 application property that is used to convey the address that a message has been
     * originally published to by a device.
     */
    public static final String APP_PROPERTY_ORIG_ADDRESS = "orig_address";
    /**
     * The name of the AMQP 1.0 message application property containing the name of the protocol adapter over which an
     * uploaded message has originally been received.
     */
    public static final String APP_PROPERTY_ORIG_ADAPTER = "orig_adapter";
    /**
     * The name of the AMQP 1.0 message application property containing the QoS level of the message as set by the
     * device.
     */
    public static final String APP_PROPERTY_QOS = "qos";
    /**
     * The name of the AMQP 1.0 message application property containing the resource a message is addressed at.
     */
    public static final String APP_PROPERTY_RESOURCE = "resource";
    /**
     * The name of the AMQP 1.0 message application property containing the status code indicating the outcome of
     * processing a request.
     */
    public static final String APP_PROPERTY_STATUS = "status";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the tenant the device that has
     * reported the data belongs to.
     */
    public static final String APP_PROPERTY_TENANT_ID = "tenant_id";
    /**
     * The name of the AMQP 1.0 message application property containing the resource version expected by the client.
     */
    public static final String APP_PROPERTY_RESOURCE_VERSION = "resource_version";
    /**
     * The name of the AMQP 1.0 message application property containing the gateway through which a command is sent.
     */
    public static final String APP_PROPERTY_CMD_VIA = "via";

    /**
     * The AMQP 1.0 <em>delivery-count</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_DELIVERY_COUNT = "delivery-count";
    /**
     * The AMQP 1.0 <em>durable</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_DURABLE = "durable";
    /**
     * The AMQP 1.0 <em>first-acquirer</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_FIRST_ACQUIRER = "first-acquirer";
    /**
     * The AMQP 1.0 <em>priority</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_PRIORITY = "priority";
    /**
     * The AMQP 1.0 <em>ttl</em> message header property.
     */
    public static final String SYS_HEADER_PROPERTY_TTL = "ttl";

    /**
     * The AMQP 1.0 <em>absolute-expiry-time</em> message property.
     */
    public static final String SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME = "absolute-expiry-time";
    /**
     * The AMQP 1.0 <em>content-encoding</em> message property.
     */
    public static final String SYS_PROPERTY_CONTENT_ENCODING = "content-encoding";
    /**
     * The AMQP 1.0 <em>content-type</em> message property.
     */
    public static final String SYS_PROPERTY_CONTENT_TYPE = "content-type";
    /**
     * The AMQP 1.0 <em>correlation-id</em> message property.
     */
    public static final String SYS_PROPERTY_CORRELATION_ID = "correlation-id";
    /**
     * The AMQP 1.0 <em>creation-time</em> message property.
     */
    public static final String SYS_PROPERTY_CREATION_TIME = "creation-time";
    /**
     * The AMQP 1.0 <em>group-id</em> message property.
     */
    public static final String SYS_PROPERTY_GROUP_ID = "group-id";
    /**
     * The AMQP 1.0 <em>group-sequence</em> message property.
     */
    public static final String SYS_PROPERTY_GROUP_SEQUENCE = "group-sequence";
    /**
     * The AMQP 1.0 <em>message-id</em> message property.
     */
    public static final String SYS_PROPERTY_MESSAGE_ID = "message-id";
    /**
     * The AMQP 1.0 <em>reply-to</em> message property.
     */
    public static final String SYS_PROPERTY_REPLY_TO = "reply-to";
    /**
     * The AMQP 1.0 <em>reply-to-group-id</em> message property.
     */
    public static final String SYS_PROPERTY_REPLY_TO_GROUP_ID = "reply-to-group-id";
    /**
     * The AMQP 1.0 <em>subject</em> message property.
     */
    public static final String SYS_PROPERTY_SUBJECT = "subject";
    /**
     * The AMQP 1.0 <em>user-id</em> message property.
     */
    public static final String SYS_PROPERTY_USER_ID = "user-id";
    /**
     * The AMQP 1.0 <em>to</em> message property.
     */
    public static final String SYS_PROPERTY_TO = "to";

    /**
     * The time-til-disconnect value to use for indicating that a device will remain connected until further notice.
     */
    public static final int TTD_VALUE_UNLIMITED = -1;

    /**
     * The MIME type representing the String representation of a JSON Object.
     */
    public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    /**
     * The MIME type representing an opaque array of bytes.
     */
    public static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    /**
     * The MIME type representing plain text.
     */
    public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain";

    private static final Logger LOG = LoggerFactory.getLogger(MessageHelper.class);

    private MessageHelper() {
    }

    /**
     * Gets the value of a message's {@value #APP_PROPERTY_DEVICE_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getDeviceId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_DEVICE_ID, String.class);
    }

    /**
     * Gets the value of a message's {@value #APP_PROPERTY_TENANT_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getTenantId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_TENANT_ID, String.class);
    }

    /**
     * Gets the value of a message's {@value #APP_PROPERTY_GATEWAY_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getGatewayId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_GATEWAY_ID, String.class);
    }

    /**
     * Gets the value of a message's {@value #APP_PROPERTY_QOS} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static Integer getQoS(final Message msg) {
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_QOS, Integer.class);
    }

    /**
     * Gets the value of a specific <em>application property</em>.
     *
     * @param <T> The expected type of the property to retrieve the value of.
     * @param props The application properties to retrieve the value from.
     * @param name The property name.
     * @param type The expected value type.
     * @return The value or {@code null} if the properties do not contain a value of the expected type for the given
     *         name.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getApplicationProperty(final ApplicationProperties props, final String name,
            final Class<T> type) {
        if (props == null) {
            return null;
        } else {
            final Object value = props.getValue().get(name);
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
     * @return The message body parsed into a JSON object or {@code null} if the message does not have a <em>Data</em>
     *         nor an <em>AmqpValue</em> section or if the body section is empty.
     * @throws NullPointerException if the message is {@code null}.
     * @throws io.vertx.core.json.DecodeException if the payload cannot be parsed into a JSON object.
     */
    public static JsonObject getJsonPayload(final Message msg) {

        return Optional.ofNullable(getPayload(msg))
                .filter(b -> b.length() > 0)
                .map(Buffer::toJsonObject)
                .orElse(null);
    }

    /**
     * Gets the payload data contained in a message's body.
     * <p>
     * The bytes in the returned buffer are determined as follows:
     * <ul>
     * <li>If the body is a Data section, the bytes contained in the
     * Data section are returned.</li>
     * <li>If the body is an AmqpValue section and contains a byte array,
     * the bytes in the array are returned.</li>
     * <li>If the body is an AmqpValue section and contains a String,
     * the String's UTF-8 encoding is returned.</li>
     * <li>In all other cases, {@code null} is returned.</li>
     * </ul>
     *
     * @param msg The AMQP 1.0 message to parse the body of.
     * @return The bytes representing the payload or {@code null} if the message
     *         neither has a <em>Data</em> nor <em>AmqpValue</em> section or if
     *         a contained <em>AmqpValue</em> section doesn't have a String or
     *         byte array value.
     * @throws NullPointerException if the message is {@code null}.
     */
    public static Buffer getPayload(final Message msg) {
        Objects.requireNonNull(msg);
        return Optional.ofNullable(getPayloadByteArray(msg)).map(Buffer::buffer).orElse(null);
    }

    /**
     * Gets the payload data contained in a message's body as a String.
     * <p>
     * The String returned is created as follows:
     * <ul>
     * <li>If the body is a Data section, the String is created by
     * interpreting the bytes as UTF-8 encoded characters.</li>
     * <li>If the body is an AmqpValue section and contains a byte array,
     * the String is created by interpreting the bytes as UTF-8 encoded characters.</li>
     * <li>If the body is an AmqpValue section and contains a String,
     * the String is returned as is.</li>
     * <li>In all other cases, {@code null} is returned.</li>
     * </ul>
     *
     * @param msg The AMQP 1.0 message to parse the body of.
     * @return The String representation of the payload data or {@code null} if the
     *         type or value type of the message body section isn't supported.
     * @throws NullPointerException if the message is {@code null}.
     */
    public static String getPayloadAsString(final Message msg) {
        Objects.requireNonNull(msg);
        // In case of an AmqpValue containing a String,
        // we prevent encoding/decoding of the String to/from its UTF-8 bytes.
        if (msg.getBody() instanceof AmqpValue
                && ((AmqpValue) msg.getBody()).getValue() instanceof String) {
            return (String) ((AmqpValue) msg.getBody()).getValue();
        }
        return Optional.ofNullable(getPayload(msg)).map(Buffer::toString).orElse(null);
    }

    /**
     * Gets the size of the payload data contained in a message's body.
     * <p>
     * If there is no body section or if its type is unsupported, {@code 0} is returned.
     *
     * @param msg The AMQP 1.0 message to parse the body of.
     * @return The payload size in bytes, 0 if message body is {@code null} or unsupported.
     * @throws NullPointerException if the message is {@code null}.
     */
    public static int getPayloadSize(final Message msg) {
        Objects.requireNonNull(msg);
        return Optional.ofNullable(getPayloadByteArray(msg)).map(bytes -> bytes.length).orElse(0);
    }

    private static byte[] getPayloadByteArray(final Message msg) {
        Objects.requireNonNull(msg);
        if (msg.getBody() == null) {
            LOG.trace("message has no body");
            return null;
        }

        if (msg.getBody() instanceof Data) {
            final Data body = (Data) msg.getBody();
            return body.getValue().getArray();
        } else if (msg.getBody() instanceof AmqpValue) {
            final AmqpValue body = (AmqpValue) msg.getBody();
            if (body.getValue() instanceof byte[]) {
                return (byte[]) body.getValue();
            } else if (body.getValue() instanceof String) {
                return ((String) body.getValue()).getBytes(StandardCharsets.UTF_8);
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
     * @throws NullPointerException if any of the parameters are {@code null}.
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
     * @throws NullPointerException if any of the parameters are {@code null}.
     *
     */
    public static void addDeviceId(final Message msg, final String deviceId) {
        addProperty(msg, APP_PROPERTY_DEVICE_ID, deviceId);
    }

    /**
     * Adds a caching directive to an AMQP 1.0 message.
     * <p>
     * The directive is put to the message's <em>application-properties</em> under key
     * {@link #APP_PROPERTY_CACHE_CONTROL}.
     *
     * @param msg The message to add the directive to.
     * @param cacheDirective The cache directive.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addCacheDirective(final Message msg, final CacheDirective cacheDirective) {
        addProperty(msg, APP_PROPERTY_CACHE_CONTROL, cacheDirective.toString());
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_CACHE_CONTROL} application property.
     *
     * @param msg The message to get the property from.
     * @return The property value or {@code null} if not set.
     */
    public static String getCacheDirective(final Message msg) {
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_CACHE_CONTROL, String.class);
    }

    /**
     * Sets the <em>time-to-live</em> for the given AMQP 1.0 message.
     *
     * @param message The message whose <em>time-to-live</em> is to be set.
     * @param timeToLive The <em>time-to-live</em> duration to be set on the message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void setTimeToLive(final Message message, final Duration timeToLive) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(timeToLive);

        message.setTtl(timeToLive.toMillis());
    }

    /**
     * Adds a property indicating a device's <em>time until disconnect</em> property to an AMQP 1.0 message.
     * <p>
     * The value is put to the message's <em>application-properties</em> under key
     * {@value CommandConstants#MSG_PROPERTY_DEVICE_TTD}.
     *
     * @param msg The message to add the property to.
     * @param timeUntilDisconnect The value of the property (number of seconds).
     */
    public static void addTimeUntilDisconnect(final Message msg, final int timeUntilDisconnect) {
        addProperty(msg, CommandConstants.MSG_PROPERTY_DEVICE_TTD, timeUntilDisconnect);
    }

    /**
     * Gets the value of a message's {@value CommandConstants#MSG_PROPERTY_DEVICE_TTD} application property.
     *
     * @param msg The message to get the property from.
     * @return The property value or {@code null} if not set.
     */
    public static Integer getTimeUntilDisconnect(final Message msg) {
        return getApplicationProperty(
                msg.getApplicationProperties(),
                CommandConstants.MSG_PROPERTY_DEVICE_TTD,
                Integer.class);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_STATUS} application property.
     *
     * @param msg The message to get the property from.
     * @return The property value or {@code null} if not set.
     */
    public static Integer getStatus(final Message msg) {
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_STATUS, Integer.class);
    }

    /**
     * Adds a property indicating the outcome of an operation to a (response) message.
     * <p>
     * The value will be stored in the message's  {@link #APP_PROPERTY_STATUS} application property.
     *
     * @param msg The message to add the status to.
     * @param status The status to set.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addStatus(final Message msg, final int status) {
        addProperty(msg, APP_PROPERTY_STATUS, status);
    }

    /**
     * Adds a property to an AMQP 1.0 message.
     * <p>
     * The property is added to the message's <em>application-properties</em>.
     *
     * @param msg The message.
     * @param key The property key.
     * @param value The property value.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addProperty(final Message msg, final String key, final Object value) {

        Objects.requireNonNull(msg);
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        final ApplicationProperties props = Optional.ofNullable(msg.getApplicationProperties())
                .orElseGet(() -> {
                    final ApplicationProperties result = new ApplicationProperties(new HashMap<>());
                    msg.setApplicationProperties(result);
                    return result;
                });
        props.getValue().put(key, value);
    }

    /**
     * Adds several AMQP 1.0 message <em>annotations</em> to the given message that are used to process/route the
     * message.
     * <p>
     * In particular, the following annotations are added:
     * <ul>
     * <li>{@link #APP_PROPERTY_TENANT_ID} - the tenant ID segment of the resource identifier</li>
     * <li>{@link #APP_PROPERTY_DEVICE_ID} - the resource ID segment of the resource identifier (if not
     * {@code null}</li>
     * <li>{@link #APP_PROPERTY_RESOURCE} - the full resource path including the endpoint, the tenant and the resource
     * ID</li>
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
     * Sets the <em>creation-time</em> of an AMQP 1.0 message
     * to the current point in time.
     * <p>
     * This method does nothing if the message already has a creation time (&gt; 0) set.
     *
     * @param msg The message to set the property on.
     */
    public static void setCreationTime(final Message msg) {
        if (msg.getCreationTime() == 0) {
            msg.setCreationTime(Instant.now().toEpochMilli());
        }
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     * <p>
     * The message's <em>content-type</em> will be set to {@link #CONTENT_TYPE_APPLICATION_JSON}.
     * The message's Data section will contain the UTF-8 encoding of the given JSON object.
     * </p>
     *
     * @param message The message.
     * @param payload The payload or {@code null} if there is no payload to convey in the message body.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setJsonPayload(final Message message, final JsonObject payload) {
        Objects.requireNonNull(message);

        setPayload(message, CONTENT_TYPE_APPLICATION_JSON, payload != null ? payload.toBuffer() : null);
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     * <p>
     * The message's <em>content-type</em> will be set to {@link #CONTENT_TYPE_APPLICATION_JSON}.
     * The message's Data section will contain the UTF-8 encoding of the given payload.
     * <b>Note:</b> No formal check is done if the payload actually is a JSON string.
     * </p>
     *
     * @param message The message.
     * @param payload The payload or {@code null} if there is no payload to convey in the message body.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setJsonPayload(final Message message, final String payload) {
        Objects.requireNonNull(message);

        setPayload(message, CONTENT_TYPE_APPLICATION_JSON,
                payload != null ? payload.getBytes(StandardCharsets.UTF_8) : null);
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     *
     * @param message The message.
     * @param contentType The type of the payload. A non-{@code null} type value will be used to set the
     *                    <em>content-type</em> property of the message, if the payload is either not {@code null}
     *                    or the {@linkplain EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION empty notification type} is
     *                    given.
     *                    If the given type is {@code null} and the payload is not {@code null}, the
     *                    {@linkplain MessageHelper#CONTENT_TYPE_OCTET_STREAM default content type} will be used.
     * @param payload The payload or {@code null} if there is no payload to convey in the message body.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setPayload(final Message message, final String contentType, final Buffer payload) {
        Objects.requireNonNull(message);

        setPayload(message, contentType, payload != null ? payload.getBytes() : null);
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     *
     * @param message The message.
     * @param contentType The type of the payload. A non-{@code null} type value will be used to set the
     *                    <em>content-type</em> property of the message, if the payload is either not {@code null}
     *                    or the {@linkplain EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION empty notification type} is
     *                    given.
     *                    If the given type is {@code null} and the payload is not {@code null}, the
     *                    {@linkplain MessageHelper#CONTENT_TYPE_OCTET_STREAM default content type} will be used.
     * @param payload The payload or {@code null} if there is no payload to convey in the message body.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setPayload(final Message message, final String contentType, final byte[] payload) {
        Objects.requireNonNull(message);

        setPayload(message, contentType, payload, true);
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     *
     * @param message The message.
     * @param contentType The type of the payload. A non-{@code null} type value will be used to set the
     *                    <em>content-type</em> property of the message, if the payload is either not {@code null}
     *                    or the {@linkplain EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION empty notification type} is
     *                    given.
     * @param payload The payload or {@code null} if there is no payload to convey in the message body.
     * @param useDefaultContentTypeAsFallback {@code true} if the {@linkplain MessageHelper#CONTENT_TYPE_OCTET_STREAM
     *                                        default content type} should be set if content type is {@code null} and
     *                                        the payload is not {@code null}.
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setPayload(
            final Message message,
            final String contentType,
            final byte[] payload,
            final boolean useDefaultContentTypeAsFallback) {

        Objects.requireNonNull(message);

        if (payload != null) {
            message.setBody(new Data(new Binary(payload)));
        }
        if ((payload != null && contentType != null)
                || EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION.equals(contentType)
                || EventConstants.CONTENT_TYPE_DEVICE_PROVISIONING_NOTIFICATION.equals(contentType)) {
            message.setContentType(contentType);
        } else if (payload != null && useDefaultContentTypeAsFallback) {
            message.setContentType(CONTENT_TYPE_OCTET_STREAM);
        }
    }

    /**
     * Checks if a message's body consists of an AMQP <em>Data</em> section.
     *
     * @param message The message to check.
     * @return {@code true} if the body consists of a Data section, {@code false} otherwise.
     * @throws NullPointerException If message is {@code null}.
     */
    public static boolean hasDataBody(final Message message) {

        Objects.requireNonNull(message);
        return message.getBody() instanceof Data;
    }

    /**
     * Gets the identifier to use for correlating to a given message.
     *
     * @param message The message to correlate to.
     * @return The value of the message's <em>correlation-id</em> property, if not {@code null}.
     *         Otherwise, the value of the <em>message-id</em> property or {@code null}
     *         if neither the message nor the correlation ID properties are set.
     */
    public static Object getCorrelationId(final Message message) {
        return Optional.ofNullable(message.getCorrelationId()).orElse(message.getMessageId());
    }

    /**
     * Set the application properties for a Proton Message but do a check for all properties first if they only contain
     * values that the AMQP 1.0 spec allows.
     *
     * @param msg The Proton message.
     * @param properties The map containing application properties.
     * @throws NullPointerException if the message passed in is {@code null}.
     * @throws IllegalArgumentException if the properties contain any value that AMQP 1.0 disallows.
     */
    public static void setApplicationProperties(final Message msg, final Map<String, ?> properties) {
        Objects.requireNonNull(msg);

        if (properties != null) {
            final Map<String, Object> propsToAdd = new HashMap<>();
            // check the three types not allowed by AMQP 1.0 spec for application properties (list, map and array)
            for (final Map.Entry<String, ?> entry : properties.entrySet()) {
                if (entry.getValue() != null) {
                    if (entry.getValue() instanceof List) {
                        throw new IllegalArgumentException(
                                String.format("Application property %s can't be a List", entry.getKey()));
                    } else if (entry.getValue() instanceof Map) {
                        throw new IllegalArgumentException(
                                String.format("Application property %s can't be a Map", entry.getKey()));
                    } else if (entry.getValue().getClass().isArray()) {
                        throw new IllegalArgumentException(
                                String.format("Application property %s can't be an Array", entry.getKey()));
                    }
                }
                propsToAdd.put(entry.getKey(), entry.getValue());
            }
            msg.setApplicationProperties(new ApplicationProperties(propsToAdd));
        }
    }
}
