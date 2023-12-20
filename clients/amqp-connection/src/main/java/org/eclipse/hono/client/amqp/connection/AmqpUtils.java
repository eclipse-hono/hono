/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.amqp.connection;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.client.amqp.tracing.AmqpMessageExtractAdapter;
import org.eclipse.hono.client.amqp.tracing.AmqpMessageInjectAdapter;
import org.eclipse.hono.client.amqp.tracing.MessageAnnotationsInjectAdapter;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;

/**
 * AMQP 1.0 related helper methods.
 *
 */
public final class AmqpUtils {

    /**
     * Indicates that an AMQP request cannot be processed due to a perceived client error.
     */
    public static final Symbol AMQP_BAD_REQUEST = Symbol.valueOf("hono:bad-request");
    /**
     * Indicates that an AMQP connection is closed due to inactivity.
     */
    public static final Symbol AMQP_ERROR_INACTIVITY = Symbol.valueOf("hono:inactivity");

    /**
     * The AMQP capability indicating support for routing messages as defined by
     * <a href="http://docs.oasis-open.org/amqp/anonterm/v1.0/anonterm-v1.0.html">
     * Anonymous Terminus for Message Routing</a>.
     */
    public static final Symbol CAP_ANONYMOUS_RELAY = Symbol.valueOf("ANONYMOUS-RELAY");

    /**
     * The key that an authenticated client's principal is stored under in a {@code ProtonConnection}'s
     * attachments.
     */
    public static final String KEY_CLIENT_PRINCIPAL = "CLIENT_PRINCIPAL";

    /**
     * The subject name to use for anonymous clients.
     */
    public static final String SUBJECT_ANONYMOUS = "ANONYMOUS";

    /**
     * The principal to use for anonymous clients.
     */
    public static final HonoUser PRINCIPAL_ANONYMOUS = new HonoUserAdapter() {

        private final Authorities authorities = new Authorities() {

            @Override
            public Map<String, Object> asMap() {
                return Collections.emptyMap();
            }

            @Override
            public boolean isAuthorized(final ResourceIdentifier resourceId, final Activity intent) {
                return false;
            }

            @Override
            public boolean isAuthorized(final ResourceIdentifier resourceId, final String operation) {
                return false;
            }
        };

        @Override
        public String getName() {
            return SUBJECT_ANONYMOUS;
        }

        @Override
        public Authorities getAuthorities() {
            return authorities;
        }
    };

    private static final String LEGACY_AMQP_ANNOTATION_NAME_TRACE_CONTEXT = "x-opt-trace-context";

    private AmqpUtils() {
        // prevent instantiation
    }


    /**
     * Gets the principal representing an authenticated peer.
     *
     * @param record The attachments to retrieve the principal from.
     * @return The principal representing the authenticated client or {@link #PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated or record is {@code null}.
     */
    private static HonoUser getClientPrincipal(final Record record) {

        if (record != null) {
            final HonoUser client = record.get(KEY_CLIENT_PRINCIPAL, HonoUser.class);
            return client != null ? client : PRINCIPAL_ANONYMOUS;
        } else {
            return PRINCIPAL_ANONYMOUS;
        }
    }

    /**
     * Gets the principal representing a connection's client.
     *
     * @param con The connection to get the principal for.
     * @return The principal representing the authenticated client or {@link #PRINCIPAL_ANONYMOUS}
     *         if the client has not been authenticated.
     * @throws NullPointerException if the connection is {@code null}.
     */
    public static HonoUser getClientPrincipal(final ProtonConnection con) {
        final Record attachments = Objects.requireNonNull(con).attachments();
        return getClientPrincipal(attachments);
    }

    /**
     * Gets the principal representing a connection's client.
     *
     * @param con The connection to get the principal for.
     * @param principal The principal representing the authenticated client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void setClientPrincipal(final ProtonConnection con, final HonoUser principal) {
        Objects.requireNonNull(principal);
        final Record attachments = Objects.requireNonNull(con).attachments();
        attachments.set(KEY_CLIENT_PRINCIPAL, HonoUser.class, principal);
    }

    /**
     * Injects a {@code SpanContext} into an AMQP {@code Message}.
     * <p>
     * The span context will be written either to the message annotations (if {@code useLegacyTraceContextFormat} is
     * {@code true}) or the application properties of the given message.
     *
     * @param tracer The Tracer to use for injecting the context.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @param message The AMQP {@code Message} object to inject the context into.
     * @param useLegacyTraceContextFormat If {@code true}, the legacy trace context format will be used (writing to
     *                                    a map in the message annotation properties).
     * @throws NullPointerException if tracer or message is {@code null}.
     */
    public static void injectSpanContext(final Tracer tracer, final SpanContext spanContext, final Message message,
            final boolean useLegacyTraceContextFormat) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(message);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            final TextMap injectAdapter = useLegacyTraceContextFormat
                    ? new MessageAnnotationsInjectAdapter(message, LEGACY_AMQP_ANNOTATION_NAME_TRACE_CONTEXT)
                    : new AmqpMessageInjectAdapter(message);
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP, injectAdapter);
        }
    }

    /**
     * Extracts a {@code SpanContext} from an AMQP {@code Message}.
     * <p>
     * The span context will be read from the message annotations or the application properties of the given message.
     *
     * @param tracer The Tracer to use for extracting the context.
     * @param message The AMQP {@code Message} to extract the context from.
     * @return The context or {@code null} if the given {@code Message} does not contain a context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static SpanContext extractSpanContext(final Tracer tracer, final Message message) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(message);

        return tracer.extract(Format.Builtin.TEXT_MAP,
                new AmqpMessageExtractAdapter(message, LEGACY_AMQP_ANNOTATION_NAME_TRACE_CONTEXT));
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
     * Gets the value of one of a message's <em>application properties</em>.
     *
     * @param <T> The expected type of the property to retrieve the value of.
     * @param message The message containing the application properties to retrieve the value from.
     * @param name The property name.
     * @param type The expected value type.
     * @return The value or {@code null} if the message's application properties do not contain a value of the
     *         expected type for the given name.
     */
    public static <T> T getApplicationProperty(
            final Message message,
            final String name,
            final Class<T> type) {
        return Optional.ofNullable(message)
                .flatMap(msg -> Optional.ofNullable(msg.getApplicationProperties()))
                .map(ApplicationProperties::getValue)
                .map(props -> props.get(name))
                .filter(type::isInstance)
                .map(type::cast)
                .orElse(null);
    }

    /**
     * Adds a caching directive to an AMQP 1.0 message.
     * <p>
     * The directive is put to the message's <em>application-properties</em> under key
     * {@value MessageHelper#APP_PROPERTY_CACHE_CONTROL}.
     *
     * @param msg The message to add the directive to.
     * @param cacheDirective The cache directive.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addCacheDirective(final Message msg, final CacheDirective cacheDirective) {
        addProperty(msg, MessageHelper.APP_PROPERTY_CACHE_CONTROL, cacheDirective.toString());
    }

    /**
     * Gets the value of a message's {@value MessageHelper#APP_PROPERTY_CACHE_CONTROL} application property.
     *
     * @param msg The message to get the property from.
     * @return The property value or {@code null} if not set.
     */
    public static String getCacheDirective(final Message msg) {
        return getApplicationProperty(msg, MessageHelper.APP_PROPERTY_CACHE_CONTROL, String.class);
    }

    /**
     * Adds a tenant ID to a message's <em>application properties</em>.
     * <p>
     * The name of the application property is {@value MessageHelper#APP_PROPERTY_TENANT_ID}.
     *
     * @param msg The message.
     * @param tenantId The tenant identifier to add.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addTenantId(final Message msg, final String tenantId) {
        addProperty(msg, MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
    }

    /**
     * Gets the value of a message's {@value MessageHelper#APP_PROPERTY_TENANT_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getTenantId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg, MessageHelper.APP_PROPERTY_TENANT_ID, String.class);
    }

    /**
     * Adds a device ID to a message's <em>application properties</em>.
     * <p>
     * The name of the application property is {@value MessageHelper#APP_PROPERTY_DEVICE_ID}.
     *
     * @param msg The message.
     * @param deviceId The device identifier to add.
     * @throws NullPointerException if any of the parameters are {@code null}.
     *
     */
    public static void addDeviceId(final Message msg, final String deviceId) {
        addProperty(msg, MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
    }

    /**
     * Gets the value of a message's {@value MessageHelper#APP_PROPERTY_DEVICE_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getDeviceId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg, MessageHelper.APP_PROPERTY_DEVICE_ID, String.class);
    }

    /**
     * Gets the value of a message's {@value MessageHelper#APP_PROPERTY_GATEWAY_ID} application property.
     *
     * @param msg The message.
     * @return The property value or {@code null} if not set.
     * @throws NullPointerException if message is {@code null}.
     */
    public static String getGatewayId(final Message msg) {
        Objects.requireNonNull(msg);
        return getApplicationProperty(msg, MessageHelper.APP_PROPERTY_GATEWAY_ID, String.class);
    }

    /**
     * Gets the value of a message's {@value MessageHelper#APP_PROPERTY_STATUS} application property.
     *
     * @param msg The message to get the property from.
     * @return The property value or {@code null} if not set.
     */
    public static Integer getStatus(final Message msg) {
        return getApplicationProperty(msg, MessageHelper.APP_PROPERTY_STATUS, Integer.class);
    }

    /**
     * Adds a property indicating the outcome of an operation to a (response) message.
     * <p>
     * The value will be stored in the message's  {@value MessageHelper#APP_PROPERTY_STATUS} application property.
     *
     * @param msg The message to add the status to.
     * @param status The status to set.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static void addStatus(final Message msg, final int status) {
        addProperty(msg, MessageHelper.APP_PROPERTY_STATUS, status);
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
        return getApplicationProperty(msg, CommandConstants.MSG_PROPERTY_DEVICE_TTD, Integer.class);
    }

    /**
     * Sets the <em>creation-time</em> of an AMQP 1.0 message to the current point in time.
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

        return null;
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     * <p>
     * The message's <em>content-type</em> will be set to {@value MessageHelper#CONTENT_TYPE_APPLICATION_JSON}.
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

        setPayload(
                message,
                MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                Optional.ofNullable(payload).map(JsonObject::toBuffer).orElse(null));
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     * <p>
     * The message's <em>content-type</em> will be set to {@value MessageHelper#CONTENT_TYPE_APPLICATION_JSON}.
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

        setPayload(
                message,
                MessageHelper.CONTENT_TYPE_APPLICATION_JSON,
                Optional.ofNullable(payload).map(Buffer::buffer).orElse(null));
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     *
     * @param message The message.
     * @param contentType The type of the payload.
     * @param payload The payload.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setPayload(final Message message, final String contentType, final Buffer payload) {
        Objects.requireNonNull(message);
        setPayload(message, contentType, Optional.ofNullable(payload).map(Buffer::getBytes).orElse(null));
    }

    /**
     * Sets the payload of an AMQP message using a <em>Data</em> section.
     *
     * @param message The message.
     * @param contentType The type of the payload or {@code null} if unknown.
     * @param payload The payload or {@code null}.
     *
     * @throws NullPointerException If message is {@code null}.
     */
    public static void setPayload(final Message message, final String contentType, final byte[] payload) {
        Objects.requireNonNull(message);

        Optional.ofNullable(contentType).ifPresent(message::setContentType);
        Optional.ofNullable(payload)
            .map(Binary::new)
            .map(Data::new)
            .ifPresent(message::setBody);
    }

    /**
     * Adds several AMQP 1.0 message <em>annotations</em> to the given message that are used to process/route the
     * message.
     * <p>
     * In particular, the following annotations are added:
     * <ul>
     * <li>{@value MessageHelper#APP_PROPERTY_TENANT_ID} - the tenant ID segment of the resource identifier</li>
     * <li>{@value MessageHelper#APP_PROPERTY_DEVICE_ID} - the resource ID segment of the resource identifier (if not
     * {@code null}</li>
     * <li>{@value MessageHelper#APP_PROPERTY_RESOURCE} - the full resource path including the endpoint, the tenant
     * and the resource ID</li>
     * </ul>
     *
     * @param msg the message to add the message annotations to.
     * @param resourceIdentifier the resource identifier that will be added as annotation.
     */
    public static void annotate(final Message msg, final ResourceIdentifier resourceIdentifier) {
        addAnnotation(msg, MessageHelper.APP_PROPERTY_TENANT_ID, resourceIdentifier.getTenantId());
        if (resourceIdentifier.getResourceId() != null) {
            addAnnotation(msg, MessageHelper.APP_PROPERTY_DEVICE_ID, resourceIdentifier.getResourceId());
        }
        addAnnotation(msg, MessageHelper.APP_PROPERTY_RESOURCE, resourceIdentifier.toString());
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
}
