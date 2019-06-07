/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.message.Message;

import io.opentracing.SpanContext;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;

/**
 * A wrapper around a JSON object which can be used to convey request and/or response
 * information for Hono API operations via the vert.x event bus.
 *
 */
public class EventBusMessage {

    private static final String FIELD_CORRELATION_ID = "correlation-id";
    private static final String FIELD_CORRELATION_ID_TYPE = "correlation-id-type";

    private final JsonObject json;
    private transient SpanContext spanContext;

    private EventBusMessage(final String operation) {
        Objects.requireNonNull(operation);
        this.json = new JsonObject();
        json.put(MessageHelper.SYS_PROPERTY_SUBJECT, operation);
    }

    private EventBusMessage(final JsonObject request) {
        this.json = Objects.requireNonNull(request);
    }

    /**
     * Creates a new (request) message for an operation.
     * 
     * @param operation The name of the operation.
     * @return The request message.
     * @throws NullPointerException if operation is {@code null}.
     */
    public static EventBusMessage forOperation(final String operation) {
        return new EventBusMessage(operation);
    }

    /**
     * Creates a new (request) message from an AMQP 1.0 message.
     * <p>
     * The operation will be determined from the message's
     * <em>subject</em>.
     * 
     * @param message The AMQP message.
     * @return The request message.
     * @throws NullPointerException if message is {@code null}.
     * @throws IllegalArgumentException if the message has no subject set.
     */
    public static EventBusMessage forOperation(final Message message) {
        if (message.getSubject() == null) {
            throw new IllegalArgumentException("message has no subject");
        } else {
            return new EventBusMessage(message.getSubject());
        }
    }

    /**
     * Creates a new (response) message for a status code.
     * 
     * @param status The status code indicating the outcome of the operation.
     * @return The response message.
     */
    public static EventBusMessage forStatusCode(final int status) {
        final EventBusMessage result = new EventBusMessage(new JsonObject());
        result.setProperty(MessageHelper.APP_PROPERTY_STATUS, status);
        return result;
    }

    /**
     * Creates a new response message in reply to this (request) message.
     * <p>
     * This method copies the following properties from the request to
     * the response (if not {@code null}):
     * <ul>
     * <li><em>status</em></li>
     * <li><em>operation</em></li>
     * <li><em>correlationId</em></li>
     * <li><em>replyToAddress</em></li>
     * <li><em>tenant</em></li>
     * </ul>
     * 
     * @param status The status code indicating the outcome of the operation.
     * @return The response message.
     * @throws NullPointerException if request is {@code null}.
     */
    public EventBusMessage getResponse(final int status) {

        final EventBusMessage reply = forStatusCode(status);
        reply.setProperty(
                MessageHelper.SYS_PROPERTY_SUBJECT,
                getProperty(MessageHelper.SYS_PROPERTY_SUBJECT));
        reply.setProperty(
                MessageHelper.SYS_PROPERTY_CORRELATION_ID,
                getProperty(MessageHelper.SYS_PROPERTY_CORRELATION_ID));
        reply.setReplyToAddress(getReplyToAddress());
        reply.setTenant(getTenant());
        return reply;
    }

    /**
     * Creates a new message from a JSON object.
     * <p>
     * Whether the created message represents a request or a response
     * is determined by the <em>status</em> and <em>operation</em> properties.
     * 
     * @param json The JSON object.
     * @return The message.
     */
    public static EventBusMessage fromJson(final JsonObject json) {
        return new EventBusMessage(Objects.requireNonNull(json));
    }

    /**
     * Checks if this (response) message has all properties required
     * for successful delivery to the client.
     * 
     * @return {@code true} if this message has {@code non-null} values for
     *         properties <em>operation</em>, <em>replyToAddress</em> and
     *         <em>correlationId</em>.
     */
    public boolean hasResponseProperties() {
        return getOperation() != null && getReplyToAddress() != null
                && getProperty(MessageHelper.SYS_PROPERTY_CORRELATION_ID) != null;
    }

    /**
     * Gets the operation to invoke.
     * 
     * @return The operation of {@code null} if this is a response message.
     */
    public String getOperation() {
        return getProperty(MessageHelper.SYS_PROPERTY_SUBJECT);
    }

    /**
     * Gets the status code indicating the outcome of the invocation
     * of the operation.
     * 
     * @return The status code or {@code null} if this is a request message.
     */
    public Integer getStatus() {
        return getProperty(MessageHelper.APP_PROPERTY_STATUS);
    }

    /**
     * Checks if the status code indicating the outcome of the invocation
     * of the operation represents an error.
     *
     * @return {@code true} if this is a response message and the status code represents an error.
     */
    public boolean hasErrorStatus() {
        final Integer status = getStatus();
        if (status == null) {
            return false;
        }
        return status >= HttpURLConnection.HTTP_BAD_REQUEST &&
                status < 600;
    }

    /**
     * Adds a property for the address that responses to
     * this (request) message should be sent.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param address The address.
     * @return This message for chaining.
     */
    public EventBusMessage setReplyToAddress(final String address) {
        setProperty(MessageHelper.SYS_PROPERTY_REPLY_TO, address);
        return this;
    }

    /**
     * Adds a property for the address that responses to
     * this (request) message should be sent to.
     * <p>
     * The property will only be added if the AMQP message contains
     * a non-{@code null} <em>reply-to</em> property.
     * 
     * @param msg The AMQP message to retrieve the value from.
     * @return This message for chaining.
     */
    public EventBusMessage setReplyToAddress(final Message msg) {
        setReplyToAddress(msg.getReplyTo());
        return this;
    }

    /**
     * Gets the value of the reply-to address property.
     * 
     * @return The value or {@code null} if not set.
     */
    public String getReplyToAddress() {
        return getProperty(MessageHelper.SYS_PROPERTY_REPLY_TO);
    }

    /**
     * Adds a property for the tenant identifier.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param tenantId The tenant identifier.
     * @return This message for chaining.
     */
    public EventBusMessage setTenant(final String tenantId) {
        setProperty(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        return this;
    }

    /**
     * Adds a property for the tenant identifier.
     * <p>
     * The property will only be added if the AMQP message contains
     * a non-{@code null} tenant identifier.
     * 
     * @param msg The AMQP message to retrieve the value from.
     * @return This message for chaining.
     */
    public EventBusMessage setTenant(final Message msg) {
        setTenant(MessageHelper.getTenantId(msg));
        return this;
    }

    /**
     * Gets the value of the tenant identifier property.
     * 
     * @return The value or {@code null} if not set.
     */
    public String getTenant() {
        return getProperty(MessageHelper.APP_PROPERTY_TENANT_ID);
    }

    /**
     * Adds a property for the device identifier.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param deviceId The device identifier.
     * @return This message for chaining.
     */
    public EventBusMessage setDeviceId(final String deviceId) {
        setProperty(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return this;
    }

    /**
     * Adds a property for the device identifier.
     * <p>
     * The property will only be added if the AMQP message contains
     * a non-{@code null} device identifier.
     * 
     * @param msg The AMQP message to retrieve the value from.
     * @return This message for chaining.
     */
    public EventBusMessage setDeviceId(final Message msg) {
        setDeviceId(MessageHelper.getDeviceId(msg));
        return this;
    }

    /**
     * Gets the value of the device identifier property.
     * 
     * @return The value or {@code null} if not set.
     */
    public String getDeviceId() {
        return getProperty(MessageHelper.APP_PROPERTY_DEVICE_ID);
    }

    /**
     * Gets the value of the object identifier property.
     *
     * @return The value or {@code null} if not set.
     */
    public String getObjectId() {
        return getJsonPayload().getString(RequestResponseApiConstants.FIELD_OBJECT_ID);
    }

    /**
     * Adds a property for the request/response payload.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param payload The payload.
     * @return This message for chaining.
     */
    public EventBusMessage setJsonPayload(final JsonObject payload) {
        setProperty(RequestResponseApiConstants.FIELD_PAYLOAD, payload);
        return this;
    }

    /**
     * Adds a property for the request/response payload.
     * <p>
     * The property will only be added if the AMQP message contains
     * a JSON payload.
     * 
     * @param msg The AMQP message to retrieve the payload from.
     * @return This message for chaining.
     * @throws DecodeException if the payload of the AMQP message does not contain proper JSON.
     */
    public EventBusMessage setJsonPayload(final Message msg) {
        setJsonPayload(MessageHelper.getJsonPayload(msg));
        return this;
    }

    /**
     * Gets the value of the payload property.
     * 
     * @return The value or {@code null} if not set.
     */
    public JsonObject getJsonPayload() {
        return getProperty(RequestResponseApiConstants.FIELD_PAYLOAD);
    }

    /**
     * Gets the value of the payload property.
     * 
     * @param defaultValue The default value.
     * @return The value of the payload property or the given default
     *         value if not set.
     */
    public JsonObject getJsonPayload(final JsonObject defaultValue) {
        final JsonObject payload = getProperty(RequestResponseApiConstants.FIELD_PAYLOAD);
        return Optional.ofNullable(payload).orElse(defaultValue);
    }

    /**
     * Adds a property for the gateway identifier.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param id The gateway identifier.
     * @return This message for chaining.
     */
    public EventBusMessage setGatewayId(final String id) {
        setProperty(MessageHelper.APP_PROPERTY_GATEWAY_ID, id);
        return this;
    }

    /**
     * Adds a property for the gateway identifier.
     * <p>
     * The property will only be added if the AMQP message contains
     * a non-{@code null} gateway identifier.
     * 
     * @param msg The AMQP message to retrieve the value from.
     * @return This message for chaining.
     */
    public EventBusMessage setGatewayId(final Message msg) {
        setStringProperty(MessageHelper.APP_PROPERTY_GATEWAY_ID, msg);
        return this;
    }

    /**
     * Gets the value of the gateway identifier property.
     * 
     * @return The value or {@code null} if not set.
     */
    public String getGatewayId() {
        return getProperty(MessageHelper.APP_PROPERTY_GATEWAY_ID);
    }

    /**
     * Adds a property for the cache directive.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param directive The cache directive.
     * @return This message for chaining.
     */
    public EventBusMessage setCacheDirective(final CacheDirective directive) {
        if (directive != null) {
            setProperty(
                    MessageHelper.APP_PROPERTY_CACHE_CONTROL,
                    Objects.requireNonNull(directive).toString());
        }
        return this;
    }

    /**
     * Gets the value of the cache directive property.
     * 
     * @return The value or {@code null} if not set.
     */
    public String getCacheDirective() {
        return getProperty(MessageHelper.APP_PROPERTY_CACHE_CONTROL);
    }

    /**
     * Adds a property for the correlation identifier.
     * <p>
     * The value of the property is set
     * <ol>
     * <li>to the AMQP message's correlation identifier, if not {@code null}, or</li>
     * <li>to the AMQP message's message identifier, if not {@code null}.</li>
     * </ol>
     * 
     * @param message The AMQP message to retrieve the value from.
     * @return This message for chaining.
     * @throws IllegalArgumentException if the message doesn't contain a correlation id
     *            nor a message id.
     */
    public EventBusMessage setCorrelationId(final Message message) {
        final Object correlationId = MessageHelper.getCorrelationId(message);
        if (correlationId == null) {
            throw new IllegalArgumentException("message does not contain message-id nor correlation-id");
        } else {
            setCorrelationId(correlationId);
            return this;
        }
    }

    /**
     * Adds a property for the correlation identifier.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param id The correlation identifier.
     * @return This message for chaining.
     * @throws IllegalArgumentException if the identifier is neither a {@code String}
     *                 nor an {@code UnsignedLong} nor a {@code UUID} nor a {@code Binary}.
     */
    public EventBusMessage setCorrelationId(final Object id) {
        setProperty(MessageHelper.SYS_PROPERTY_CORRELATION_ID, encodeIdToJson(id));
        return this;
    }

    /**
     * Gets the value of the correlation identifier property.
     * 
     * @return The value or {@code null} if not set.
     */
    public Object getCorrelationId() {
        final JsonObject encodedId = getProperty(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
        if (encodedId == null) {
            return null;
        } else {
            return decodeIdFromJson(encodedId);
        }
    }

    /**
     * Adds a property for the resource version option.
     * <p>
     * The property will only be added if the value is not {@code null}.
     *
     * @param version The version value.
     * @return This message for chaining.
     */
    public EventBusMessage setResourceVersion(final String version) {
        setProperty(MessageHelper.APP_PROPERTY_RESOURCE_VERSION, version);
        return this;
    }

    /**
     * Gets the value of the cache resource version property.
     *
     * @return The value or {@code null} if not set.
     */
    public String getResourceVersion() {
        return getProperty(MessageHelper.APP_PROPERTY_RESOURCE_VERSION);
    }

    /**
     * Adds a property with a value.
     * <p>
     * The property will only be added if the value is not {@code null}.
     * 
     * @param name The name of the property.
     * @param value the value to set.
     * @return This message for chaining.
     * @throws NullPointerException if name is {@code null}.
     */
    public EventBusMessage setProperty(final String name, final Object value) {
        Objects.requireNonNull(name);
        if (value != null) {
            json.put(name, value);
        }
        return this;
    }

    /**
     * Adds a property with a value from an AMQP message.
     * <p>
     * The property will only be added if the AMQP message contains
     * a non-{@code null} <em>application property</em> of the given name.
     * 
     * @param name The name of the property.
     * @param msg The AMQP message to retrieve the value from.
     * @return This message for chaining.
     */
    public EventBusMessage setStringProperty(final String name, final Message msg) {
        setProperty(name, MessageHelper.getApplicationProperty(
                msg.getApplicationProperties(),
                name,
                String.class));
        return this;
    }

    /**
     * Gets a property value.
     * 
     * @param key The name of the property.
     * @param <T> The type of the field.
     * @return The property value or {@code null} if no such property exists or is not of the expected type.
     * @throws NullPointerException if key is {@code null}.
     */
    @SuppressWarnings({ "unchecked" })
    public <T> T getProperty(final String key) {

        Objects.requireNonNull(key);

        try {
            return (T) json.getValue(key);
        } catch (final ClassCastException e) {
            return null;
        }
    }

    /**
     * Sets an <em>OpenTracing</em> {@code SpanContext} on this message.
     * <p>
     * This may be the tracing context associated with the (request) message for which this {@code EventBusMessage} was
     * created. The span context should be used as the parent context of each OpenTracing {@code Span} that is created
     * as part of processing this event bus message.
     * <p>
     * Note: the span context instance will not get serialized when sending this event bus message over the vert.x event
     * bus!
     * 
     * @param spanContext The {@code SpanContext} to set (may be null).
     */
    public void setSpanContext(final SpanContext spanContext) {
        this.spanContext = spanContext;
    }

    /**
     * Gets the <em>OpenTracing</em> {@code SpanContext} that has been set on this message.
     * <p>
     * The span context should be used as the parent context of each OpenTracing {@code Span} that is created as part of
     * processing this event bus message.
     * 
     * @return {@code SpanContext} or {@code null}.
     */
    public SpanContext getSpanContext() {
        return spanContext;
    }

    /**
     * Creates a JSON object representation of this message.
     * <p>
     * The {@link #fromJson(JsonObject)} method can be used to create
     * a {@code EventBusMethod} from its JSON representation.
     * 
     * @return The JSOn object.
     */
    public JsonObject toJson() {
        return json.copy();
    }

    /**
     * Serializes a correlation identifier to JSON.
     * <p>
     * Supported types for AMQP 1.0 correlation IDs are
     * {@code String}, {@code UnsignedLong}, {@code UUID} and {@code Binary}.
     * 
     * @param id The identifier to encode.
     * @return The JSON representation of the identifier.
     * @throws NullPointerException if the correlation id is {@code null}.
     * @throws IllegalArgumentException if the type is not supported.
     */
    private static JsonObject encodeIdToJson(final Object id) {

        Objects.requireNonNull(id);

        final JsonObject json = new JsonObject();
        if (id instanceof String) {
            json.put(FIELD_CORRELATION_ID_TYPE, "string");
            json.put(FIELD_CORRELATION_ID, id);
        } else if (id instanceof UnsignedLong) {
            json.put(FIELD_CORRELATION_ID_TYPE, "ulong");
            json.put(FIELD_CORRELATION_ID, id.toString());
        } else if (id instanceof UUID) {
            json.put(FIELD_CORRELATION_ID_TYPE, "uuid");
            json.put(FIELD_CORRELATION_ID, id.toString());
        } else if (id instanceof Binary) {
            json.put(FIELD_CORRELATION_ID_TYPE, "binary");
            final Binary binary = (Binary) id;
            json.put(FIELD_CORRELATION_ID, Base64.getEncoder().encodeToString(binary.getArray()));
        } else {
            throw new IllegalArgumentException("type " + id.getClass().getName() + " is not supported");
        }
        return json;
    }

    /**
     * Deserializes a correlation identifier from JSON.
     * <p>
     * Supported types for AMQP 1.0 correlation IDs are
     * {@code String}, {@code UnsignedLong}, {@code UUID} and {@code Binary}.
     * 
     * @param json The JSON representation of the identifier.
     * @return The correlation identifier.
     * @throws NullPointerException if the JSON is {@code null}.
     */
    private static Object decodeIdFromJson(final JsonObject json) {
        Objects.requireNonNull(json);

        final String type = json.getString(FIELD_CORRELATION_ID_TYPE);
        final String id = json.getString(FIELD_CORRELATION_ID);
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
