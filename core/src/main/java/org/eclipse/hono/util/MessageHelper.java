/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

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
     * The name of the AMQP 1.0 message application property containing the time until disconnect of the device that is available
     * for receiving an upstream message for the given number of seconds (short for <em>Time til Disconnect</em>).
     */
    public static final String APP_PROPERTY_DEVICE_TTD = "ttd";
    /**
     * The name of the AMQP 1.0 message application property containing the id of the gateway that wants to report data
     * on behalf of another device.
     */
    public static final String APP_PROPERTY_GATEWAY_ID = "gateway_id";
    /**
     * The name of the AMQP 1.0 message application property containing the id of a protocol adapter instance.
     */
    public static final String APP_PROPERTY_ADAPTER_INSTANCE_ID = "adapter_instance_id";
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
     * The name of the AMQP 1.0 message application property containing a JWT token asserting a device's registration
     * status.
     */
    public static final String APP_PROPERTY_REGISTRATION_ASSERTION = "reg_assertion";
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
     * The {@code JMS_AMQP_CONTENT_ENCODING} vendor property name.
     */
    public static final String JMS_VENDOR_PROPERTY_CONTENT_ENCODING = "JMS_AMQP_CONTENT_ENCODING";
    /**
     * The {@code JMS_AMQP_CONTENT_TYPE} vendor property name.
     */
    public static final String JMS_VENDOR_PROPERTY_CONTENT_TYPE = "JMS_AMQP_CONTENT_TYPE";

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
     * Gets the value of a message's {@link #APP_PROPERTY_DEVICE_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getDeviceId(final Message msg) {
        Objects.requireNonNull(msg);
        return getDeviceId(msg.getApplicationProperties());
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_DEVICE_ID} application property.
     *
     * @param properties The message's application properties.
     * @return The property value or {@code null} if not set.
     */
    public static String getDeviceId(final ApplicationProperties properties) {
        return getApplicationProperty(properties, APP_PROPERTY_DEVICE_ID, String.class);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_TENANT_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getTenantId(final Message msg) {
        Objects.requireNonNull(msg);
        return getTenantId(msg.getApplicationProperties());
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_TENANT_ID} application property.
     *
     * @param properties The message's application properties.
     * @return The property value or {@code null} if not set.
     */
    public static String getTenantId(final ApplicationProperties properties) {
        return getApplicationProperty(properties, APP_PROPERTY_TENANT_ID, String.class);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_GATEWAY_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getGatewayId(final Message msg) {
        Objects.requireNonNull(msg);
        return getGatewayId(msg.getApplicationProperties());
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_GATEWAY_ID} application property.
     *
     * @param properties The message's application properties.
     * @return The property value or {@code null} if not set.
     */
    public static String getGatewayId(final ApplicationProperties properties) {
        return getApplicationProperty(properties, APP_PROPERTY_GATEWAY_ID, String.class);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_QOS} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static int getQoS(final Message msg) {
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_QOS, Integer.class);
    }

    /**
     * Gets the registration assertion conveyed in an AMQP 1.0 message.
     * <p>
     * The assertion is expected to be contained in the messages's <em>application-properties</em> under key
     * {@link #APP_PROPERTY_REGISTRATION_ASSERTION}.
     *
     * @param msg The message.
     * @return The assertion or {@code null} if the message does not contain an assertion (at the expected location).
     */
    public static String getRegistrationAssertion(final Message msg) {
        return getRegistrationAssertion(msg, false);
    }

    /**
     * Gets and removes the registration assertion conveyed in an AMQP 1.0 message.
     * <p>
     * The assertion is expected to be contained in the messages's <em>application-properties</em> under key
     * {@link #APP_PROPERTY_REGISTRATION_ASSERTION}.
     *
     * @param msg The message.
     * @return The assertion or {@code null} if the message does not contain an assertion (at the expected location).
     */
    public static String getAndRemoveRegistrationAssertion(final Message msg) {
        return getRegistrationAssertion(msg, true);
    }

    private static String getRegistrationAssertion(final Message msg, final boolean removeAssertion) {
        Objects.requireNonNull(msg);
        String assertion = null;
        final ApplicationProperties properties = msg.getApplicationProperties();
        if (properties != null) {
            final Object obj;
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
     *         nor an <em>AmqpValue</em> section.
     * @throws NullPointerException if the message is {@code null}.
     * @throws DecodeException if the payload cannot be parsed into a JSON object.
     */
    public static JsonObject getJsonPayload(final Message msg) {

        return Optional.ofNullable(getPayload(msg))
                .map(buffer -> buffer.length() > 0 ? buffer.toJsonObject() : null)
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
     * @return The bytes representing the payload or {@code null} if
     *         the message neither has a <em>Data</em> nor <em>AmqpValue</em> section.
     * @throws NullPointerException if the message is {@code null}.
     */
    public static Buffer getPayload(final Message msg) {

        Objects.requireNonNull(msg);
        if (msg.getBody() == null) {
            LOG.trace("message has no body");
            return null;
        }

        if (msg.getBody() instanceof Data) {
            final Data body = (Data) msg.getBody();
            return Buffer.buffer(body.getValue().getArray());
        } else if (msg.getBody() instanceof AmqpValue) {
            final AmqpValue body = (AmqpValue) msg.getBody();
            if (body.getValue() instanceof byte[]) {
                return Buffer.buffer((byte[]) body.getValue());
            } else if (body.getValue() instanceof String) {
                return Buffer.buffer((String) body.getValue());
            }
        }

        LOG.debug("unsupported body type [{}]", msg.getBody().getClass().getName());
        return null;
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
     * @param message The AMQP 1.0 message to parse the body of.
     * @return The String representation of the payload data or {@code null} if the message
     *         body does not contain data that can be decoded into a String.
     * @throws NullPointerException if the message is {@code null}.
     */
    public static String getPayloadAsString(final Message message) {

        Objects.requireNonNull(message);

        if (message.getBody() == null) {
            LOG.trace("message has no body");
            return null;
        }

        // The code below is almost identical to the getPayload method.
        // However, in case of an AmqpValue containing a String,
        // we prevent encoding/decoding of the String to/from its UTF-8 bytes.
        if (message.getBody() instanceof Data) {

            final Data body = (Data) message.getBody();
            return Buffer.buffer(body.getValue().getArray()).toString();

        } else if (message.getBody() instanceof AmqpValue) {

            final AmqpValue body = (AmqpValue) message.getBody();
            if (body.getValue() instanceof byte[]) {
                return Buffer.buffer((byte[]) body.getValue()).toString();
            } else if (body.getValue() instanceof String) {
                return (String) body.getValue();
            }
        }

        LOG.debug("unsupported body type [{}]", message.getBody().getClass().getName());
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
     * Adds a registration assertion to an AMQP 1.0 message.
     * <p>
     * The assertion is put to the message's <em>application-properties</em> under key
     * {@link #APP_PROPERTY_REGISTRATION_ASSERTION}.
     *
     * @param msg The message.
     * @param token The assertion to add.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addRegistrationAssertion(final Message msg, final String token) {
        addProperty(msg, APP_PROPERTY_REGISTRATION_ASSERTION, token);
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
     * The value is put to the message's <em>application-properties</em> under key {@link #APP_PROPERTY_DEVICE_TTD}.
     *
     * @param msg The message to add the property to.
     * @param timeUntilDisconnect The value of the property (number of seconds).
     */
    public static void addTimeUntilDisconnect(final Message msg, final int timeUntilDisconnect) {
        addProperty(msg, APP_PROPERTY_DEVICE_TTD, timeUntilDisconnect);
    }

    /**
     * Gets the value of a message's {@link #APP_PROPERTY_DEVICE_TTD} application property.
     *
     * @param msg The message to get the property from.
     * @return The property value or {@code null} if not set.
     */
    public static Integer getTimeUntilDisconnect(final Message msg) {
        return getApplicationProperty(msg.getApplicationProperties(), APP_PROPERTY_DEVICE_TTD, Integer.class);
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
     * Rejects and settles an AMQP 1.0 message.
     *
     * @param delivery The message's delivery handle.
     * @param error The error condition to set as the reason for rejecting the message (may be {@code null}.
     * @throws NullPointerException if delivery is {@code null}.
     */
    public static void rejected(final ProtonDelivery delivery, final ErrorCondition error) {

        Objects.requireNonNull(delivery);

        final Rejected rejected = new Rejected();
        rejected.setError(error); // doesn't matter if null
        delivery.disposition(rejected, true);
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
     * Adds JMS vendor properties defined by
     * <a href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf"> AMQP JMS
     * Mapping 1.0</a> as AMQP 1.0 application properties to a given message.
     * <p>
     * The following vendor properties are added (if the message has a corresponding non-null value set):
     * <ul>
     * <li>{@link #JMS_VENDOR_PROPERTY_CONTENT_TYPE}</li>
     * <li>{@link #JMS_VENDOR_PROPERTY_CONTENT_ENCODING}</li>
     * </ul>
     *
     * @param msg the message to add the vendor properties to.
     */
    public static void addJmsVendorProperties(final Message msg) {
        if (!Strings.isNullOrEmpty(msg.getContentType())) {
            MessageHelper.addProperty(msg, JMS_VENDOR_PROPERTY_CONTENT_TYPE, msg.getContentType());
        }
        if (!Strings.isNullOrEmpty(msg.getContentEncoding())) {
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
     * Returns the value to which the specified key is mapped in the message annotations, or {@code null} if the message
     * annotations contain no mapping for the key.
     *
     * @param <T> the expected type of the property to read.
     * @param msg the message that contains the annotations.
     * @param key the name of the symbol to return a value for.
     * @param type the expected type of the value.
     * @return the annotation's value or {@code null} if no such annotation exists or its value is not of the expected
     *         type.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAnnotation(final Message msg, final String key, final Class<T> type) {
        final MessageAnnotations annotations = msg.getMessageAnnotations();
        if (annotations == null) {
            return null;
        } else {
            final Object value = annotations.getValue().get(Symbol.getSymbol(key));
            if (type.isInstance(value)) {
                return (T) value;
            } else {
                return null;
            }
        }
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
     * Checks if a device is currently connected to a protocol adapter.
     * <p>
     * If this method returns {@code true} an attempt could be made to send a command to the device.
     * <p>
     * This method uses the message's creation time and TTD value to determine the point in time
     * until which the device will remain connected.
     *
     * @param msg The message that is checked for a TTD value.
     * @return {@code true} if the TTD value contained in the message indicates that the device will
     *         stay connected for some additional time.
     * @throws NullPointerException If msg is {@code null}.
     */
    public static boolean isDeviceCurrentlyConnected(final Message msg) {

        return Optional.ofNullable(MessageHelper.getTimeUntilDisconnect(msg)).map(ttd -> {
            if (ttd == MessageHelper.TTD_VALUE_UNLIMITED) {
                return true;
            } else if (ttd == 0) {
                return false;
            } else {
                final Instant creationTime = Instant.ofEpochMilli(msg.getCreationTime());
                return Instant.now().isBefore(creationTime.plusSeconds(ttd));
            }
        }).orElse(false);
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
     * @param contentType The type of the payload. If {@code null} the message's <em>content-type</em>
     *                    property will not be set.
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
     * @param contentType The type of the payload. The message's <em>content-type</em> property
     *                    will only be set if both this and the payload parameter are not {@code null}.
     * @param payload The payload or {@code null} if there is no payload to convey in the message body.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setPayload(final Message message, final String contentType, final byte[] payload) {
        Objects.requireNonNull(message);

        if (payload != null) {
            message.setBody(new Data(new Binary(payload)));
            if (contentType != null) {
                message.setContentType(contentType);
            }
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
     * Checks if a message's body consists of an AMQP <em>AmqpValue</em> section.
     *
     * @param message The message to check.
     * @return {@code true} if the body consists of an AmqpValue section, {@code false} otherwise.
     * @throws NullPointerException If message is {@code null}.
     */
    public static boolean hasAmqpValueBody(final Message message) {

        Objects.requireNonNull(message);
        return message.getBody() instanceof AmqpValue;
    }

    /**
     * Returns a copy of the given message.
     * <p>
     * This is a shallow copy of the <em>Message</em> object, except for the copied <em>Properties</em>.
     *
     * @param message The message to copy.
     * @return The message copy.
     */
    public static Message getShallowCopy(final Message message) {
        final Message copy = ProtonHelper.message();
        copy.setDeliveryAnnotations(message.getDeliveryAnnotations());
        copy.setMessageAnnotations(message.getMessageAnnotations());
        if (message.getProperties() != null) {
            copy.setProperties(new Properties(message.getProperties()));
        }
        copy.setApplicationProperties(message.getApplicationProperties());
        copy.setBody(message.getBody());
        copy.setFooter(message.getFooter());
        return copy;
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
     * Creates an AMQP 1.0 message.
     * <p>
     * This method simply calls
     * {@link #newMessage(QoS, ResourceIdentifier, String, String, Buffer, TenantObject, JsonObject, Integer, Duration, String, boolean, boolean)}
     * to create the AMQP 1.0 message.
     *
     * @param qos          The QoS level with which the device sent the message to the protocol adapter or {@code null}
     *                     if the corresponding <em>qos</em> application property in the AMQP message should not be set.
     * @param target       The target address of the message or {@code null} if the message's
     *                     <em>to</em> property contains the target address.
     * @param contentType  The content type describing the message's payload or {@code null} if no content type
     *                     should be set.
     * @param payload      The message payload or {@code null} if the message has no payload.
     * @param tenant       The information registered for the tenant that the device belongs to or {@code null}
     *                     if no information about the tenant is available.
     * @param timeToLive   The message's <em>time-to-live</em> as provided by the device or {@code null} if the
     *                     device did not provide any TTL.
     * @param adapterName  The type name of the protocol adapter that the message has been published to.
     *
     * @return  The AMQP 1.0 message.
     */
    public static Message newMessage(
            final QoS qos,
            final ResourceIdentifier target,
            final String contentType,
            final Buffer payload,
            final TenantObject tenant,
            final Duration timeToLive,
            final String adapterName) {

        return MessageHelper.newMessage(qos, target, null, contentType, payload, tenant, null, null, timeToLive, adapterName, false,
                false);
    }
    /**
     * Creates a new AMQP 1.0 message.
     * <p>
     * This method creates a new {@code Message} and sets
     * <ul>
     * <li>its <em>creation-time</em> to the current system time,</li>
     * <li>its <em>content-type</em> to the given value,</li>
     * <li>its payload as an AMQP <em>Data</em> section</li>
     * </ul>
     * The message is then passed to {@link #addProperties(Message, QoS, ResourceIdentifier, String, TenantObject,
     * JsonObject, Integer, Duration, String, boolean, boolean)}.
     *
     * @param qos The QoS level with which the device sent the message to the protocol adapter or {@code null}
     *            if the corresponding <em>qos</em> application property in the AMQP message should not be set.
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param publishAddress The address that the message has been published to originally by the device or
     *            {@code null} if unknown.
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param contentType The content type describing the message's payload or {@code null} if no content type
     *                    should be set.
     * @param payload The message payload or {@code null} if the message has no payload.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param deviceDefaultProperties The device's default properties registered at the device level or {@code null}
     *                                if no default properties are registered for the device.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message will disconnect
     *            from the protocol adapter or {@code null} if unknown.
     * @param timeToLive The message's <em>time-to-live</em> as provided by the device or {@code null} if the
     *                   device did not provide any TTL.
     * @param adapterTypeName The type name of the protocol adapter that the message has been published to.
     * @param addDefaults {@code true} if the default properties registered for the device should be added
     *                    to the message. The properties to add are determined by merging the properties returned
     *                    by {@link TenantObject#getDefaults()} with the given device level default properties.
     * @param addJmsVendorProps {@code true} if
     *                          <a href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     *                          JMS Vendor Properties</a> should be added to the message.

     * @return The newly created message.
     * @throws NullPointerException if adapterTypeName is {@code null}.
     */
    public static Message newMessage(
            final QoS qos,
            final ResourceIdentifier target,
            final String publishAddress,
            final String contentType,
            final Buffer payload,
            final TenantObject tenant,
            final JsonObject deviceDefaultProperties,
            final Integer timeUntilDisconnect,
            final Duration timeToLive,
            final String adapterTypeName,
            final boolean addDefaults,
            final boolean addJmsVendorProps) {

        Objects.requireNonNull(adapterTypeName);

        final Message msg = ProtonHelper.message();
        msg.setContentType(contentType);
        setPayload(msg, contentType, payload);

        return addProperties(
                msg,
                qos,
                target,
                publishAddress,
                tenant,
                deviceDefaultProperties,
                timeUntilDisconnect,
                timeToLive,
                adapterTypeName,
                addDefaults,
                addJmsVendorProps);
    }

    /**
     * Sets Hono specific properties on an AMQP 1.0 message.
     * <p>
     * The following properties are set:
     * <ul>
     * <li><em>to</em> will be set to the address consisting of the target's endpoint and tenant</li>
     * <li><em>creation-time</em> will be set to the current number of milliseconds since 1970-01-01T00:00:00Z
     * if not set already</li>
     * <li>application property <em>device_id</em> will be set to the target's resourceId property</li>
     * <li>application property <em>orig_address</em> will be set to the given publish address</li>
     * <li>application property <em>orig_adapter</em> will be set to the given adapter type name}</li>
     * <li>application property <em>ttd</em> will be set to the given time-til-disconnect</li>
     * </ul>
     *
     * In addition, this method
     * <ul>
     * <li>optionally augments the message with missing (application) properties corresponding to the default properties
     * registered at the tenant and device level. Default properties defined at
     * the device level take precedence over properties with the same name defined at the tenant level.</li>
     * <li>optionally adds JMS vendor properties.</li>
     * <li>sets the message's <em>content-type</em> to the {@linkplain #CONTENT_TYPE_OCTET_STREAM fall back content
     * type}, if the default properties do not contain a content type and the message has no content type set yet.</li>
     * <li>sets the message's <em>ttl</em> header field based on the (device provided) <em>time-to-live</em> duration
     * as specified by the <a href="https://www.eclipse.org/hono/docs/api/tenant/#resource-limits-configuration-format">
     * Tenant API</a>.</li>
     * </ul>
     *
     * @param msg The message to add the properties to.
     * @param qos The QoS level with which the device sent the message to the protocol adapter or {@code null}
     *            if the corresponding <em>qos</em> application property in the AMQP message should not be set.
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param publishAddress The address that the message has been published to originally by the device or
     *            {@code null} if unknown.
     *            <p>
     *            This address will be transport protocol specific, e.g. an HTTP based adapter will probably use URIs
     *            here whereas an MQTT based adapter might use the MQTT message's topic.
     * @param tenant The information registered for the tenant that the device belongs to or {@code null}
     *               if no information about the tenant is available.
     * @param deviceDefaultProperties The device's default properties registered at the device level or {@code null}
     *                                if no default properties are registered for the device.
     * @param timeUntilDisconnect The number of seconds until the device that has published the message will disconnect
     *            from the protocol adapter or {@code null} if unknown.
     * @param timeToLive The message's <em>time-to-live</em> as provided by the device or {@code null} if the
     *                   device did not provide any TTL.
     * @param adapterTypeName The type name of the protocol adapter that the message has been published to.
     * @param addDefaults {@code true} if the default properties registered for the device should be added
     *                    to the message. The properties to add are determined by merging the properties returned
     *                    by {@link TenantObject#getDefaults()} with the given device level default properties.
     * @param addJmsVendorProps {@code true} if
     *                          <a href="https://www.oasis-open.org/committees/download.php/60574/amqp-bindmap-jms-v1.0-wd09.pdf">
     *                          JMS Vendor Properties</a> should be added to the message.
     * @return The message with its properties set.
     * @throws NullPointerException if message or adapterTypeName are {@code null}.
     * @throws IllegalArgumentException if target is {@code null} and the message does not have an address set.
     */
    public static Message addProperties(
            final Message msg,
            final QoS qos,
            final ResourceIdentifier target,
            final String publishAddress,
            final TenantObject tenant,
            final JsonObject deviceDefaultProperties,
            final Integer timeUntilDisconnect,
            final Duration timeToLive,
            final String adapterTypeName,
            final boolean addDefaults,
            final boolean addJmsVendorProps) {

        Objects.requireNonNull(msg);
        Objects.requireNonNull(adapterTypeName);
        if (target == null && Strings.isNullOrEmpty(msg.getAddress())) {
            throw new IllegalArgumentException("message must have an address set");
        }

        final ResourceIdentifier ri = Optional.ofNullable(target)
                .orElseGet(() -> ResourceIdentifier.fromString(msg.getAddress()));

        setCreationTime(msg);
        msg.setAddress(ri.getBasePath());
        addDeviceId(msg, ri.getResourceId());
        addProperty(msg, MessageHelper.APP_PROPERTY_ORIG_ADAPTER, adapterTypeName);
        if (qos != null && qos != QoS.UNKNOWN) {
            addProperty(msg, MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        }
        annotate(msg, ri);
        if (publishAddress != null) {
            addProperty(msg, MessageHelper.APP_PROPERTY_ORIG_ADDRESS, publishAddress);
        }
        if (timeUntilDisconnect != null) {
            addTimeUntilDisconnect(msg, timeUntilDisconnect);
        }
        if (timeToLive != null) {
            setTimeToLive(msg, timeToLive);
        }

        addProperties(
                msg,
                ri,
                tenant,
                deviceDefaultProperties,
                addDefaults,
                addJmsVendorProps);
        return msg;
    }

    private static Message addProperties(
            final Message message,
            final ResourceIdentifier target,
            final TenantObject tenant,
            final JsonObject deviceDefaultProperties,
            final boolean useDefaults,
            final boolean useJmsVendorProps) {

        final long maxTtl = Optional.ofNullable(tenant)
                .map(t -> Optional.ofNullable(t.getResourceLimits())
                    .map(limits -> limits.getMaxTtl())
                    .orElse(TenantConstants.UNLIMITED_TTL))
                .orElse(TenantConstants.UNLIMITED_TTL);

        if (useDefaults) {
            final JsonObject defaults = Optional.ofNullable(tenant)
                    .map(t -> t.getDefaults().copy())
                    .orElseGet(() -> new JsonObject());
            if (deviceDefaultProperties != null) {
                defaults.mergeIn(deviceDefaultProperties);
            }

            if (!defaults.isEmpty()) {
                addDefaults(message, target, defaults, maxTtl);
            }
        }
        if (Strings.isNullOrEmpty(message.getContentType())) {
            // set default content type if none has been specified when creating the
            // message nor a default content type is available
            message.setContentType(CONTENT_TYPE_OCTET_STREAM);
        }
        if (useJmsVendorProps) {
            addJmsVendorProperties(message);
        }

        // make sure that device provided TTL is capped at max TTL (if set)
        // AMQP spec defines TTL as milliseconds
        final long maxTtlMillis = maxTtl * 1000L;
        if (target.hasEventEndpoint() && maxTtl != TenantConstants.UNLIMITED_TTL) {
            if (message.getTtl() == 0) {
                LOG.debug("setting event's TTL to tenant's max TTL [{}ms]", maxTtlMillis);
                setTimeToLive(message, Duration.ofSeconds(maxTtl));
            } else if (message.getTtl() > maxTtlMillis) {
                LOG.debug("limiting device provided TTL [{}ms] to max TTL [{}ms]", message.getTtl(), maxTtlMillis);
                setTimeToLive(message, Duration.ofSeconds(maxTtl));
            } else {
                LOG.trace("keeping event's TTL [0 < {}ms <= max TTL ({}ms)]", message.getTtl(), maxTtlMillis);
            }
        }
        return message;
    }


    /**
     * Adds default properties to an AMQP message.
     * <p>
     * This method also sets the message's <em>time-to-live</em> (TTL) property
     * as described in
     *
     * @param message The message to add the properties to.
     * @param target The target address of the message or {@code null} if the message's
     *               <em>to</em> property contains the target address. The target
     *               address is used to determine if the message represents an event or not.
     *               Determining this information from the <em>to</em> property
     *               requires additional parsing which can be prevented by passing in the
     *               target address as a {@code ResourceIdentifier} instead.
     * @param defaults The default properties (device and tenant level) to set or {@code null}
     *                 if no default properties are defined for the device that the message originates from.
     * @param maxTtl The maximum time-to-live (in seconds) that should be used for limiting a default TTL.
     * @return The message with the default properties set.
     * @throws NullPointerException if message is {@code null} or if target is {@code null}
     *                              and the message does not contain an address.
     */
    public static Message addDefaults(
            final Message message,
            final ResourceIdentifier target,
            final JsonObject defaults,
            final long maxTtl) {

        Objects.requireNonNull(message);

        if (defaults == null) {
            return message;
        }

        final ResourceIdentifier ri = Optional.ofNullable(target)
                .orElseGet(() -> ResourceIdentifier.fromString(message.getAddress()));

        defaults.forEach(prop -> {

            switch (prop.getKey()) {
            case MessageHelper.SYS_HEADER_PROPERTY_TTL:
                if (ri.hasEventEndpoint() && message.getTtl() == 0 && Number.class.isInstance(prop.getValue())) {
                    final long defaultTtl = ((Number) prop.getValue()).longValue();
                    if (maxTtl != TenantConstants.UNLIMITED_TTL && defaultTtl > maxTtl) {
                        MessageHelper.setTimeToLive(message, Duration.ofSeconds(maxTtl));
                    } else {
                        MessageHelper.setTimeToLive(message, Duration.ofSeconds(defaultTtl));
                    }
                }
                break;
            case SYS_PROPERTY_CONTENT_TYPE:
                if (Strings.isNullOrEmpty(message.getContentType()) && String.class.isInstance(prop.getValue())) {
                    // set to default type registered for device or fall back to default content type
                    message.setContentType((String) prop.getValue());
                }
                break;
            case SYS_PROPERTY_CONTENT_ENCODING:
                if (Strings.isNullOrEmpty(message.getContentEncoding()) && String.class.isInstance(prop.getValue())) {
                    message.setContentEncoding((String) prop.getValue());
                }
                break;
            case SYS_HEADER_PROPERTY_DELIVERY_COUNT:
            case SYS_HEADER_PROPERTY_DURABLE:
            case SYS_HEADER_PROPERTY_FIRST_ACQUIRER:
            case SYS_HEADER_PROPERTY_PRIORITY:
            case SYS_PROPERTY_ABSOLUTE_EXPIRY_TIME:
            case SYS_PROPERTY_CORRELATION_ID:
            case SYS_PROPERTY_CREATION_TIME:
            case SYS_PROPERTY_GROUP_ID:
            case SYS_PROPERTY_GROUP_SEQUENCE:
            case SYS_PROPERTY_MESSAGE_ID:
            case SYS_PROPERTY_REPLY_TO:
            case SYS_PROPERTY_REPLY_TO_GROUP_ID:
            case SYS_PROPERTY_SUBJECT:
            case SYS_PROPERTY_TO:
            case SYS_PROPERTY_USER_ID:
                // these standard properties cannot be set using defaults
                LOG.debug("ignoring default property [{}] registered for device", prop.getKey());
                break;
            default:
                // add all other defaults as application properties
                addProperty(message, prop.getKey(), prop.getValue());
            }
        });

        return message;
    }

}
