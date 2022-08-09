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

package org.eclipse.hono.tracing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.noop.NoopSpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.BooleanTag;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tags;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

/**
 * A helper class providing utility methods for interacting with the
 * OpenTracing API.
 *
 */
public final class TracingHelper {

    /**
     * An OpenTracing tag indicating the identifier of a protocol adapter instance.
     */
    public static final StringTag TAG_ADAPTER_INSTANCE_ID = new StringTag(CommandConstants.MSG_PROPERTY_ADAPTER_INSTANCE_ID);
    /**
     * An OpenTracing tag indicating if a client (device) has been authenticated.
     */
    public static final BooleanTag TAG_AUTHENTICATED = new BooleanTag("authenticated");
    /**
     * An OpenTracing tag that contains the authentication identifier used by a device.
     */
    public static final StringTag TAG_AUTH_ID = new StringTag("auth_id");
    /**
     * An OpenTracing tag that is used to indicate if the result of an operation
     * has been taken from a local cache.
     */
    public static final BooleanTag TAG_CACHE_HIT = new BooleanTag("cache_hit");
    /**
     * An OpenTracing tag that contains the (transport protocol specific) identifier of a
     * client connecting to a server. This could be the MQTT <em>client identifier</em> or the
     * AMQP 1.0 <em>container name</em>.
     */
    public static final StringTag TAG_CLIENT_ID = new StringTag("client_id");
    /**
     * An OpenTracing tag that contains the identifier used to correlate a response
     * with a request message.
     */
    public static final StringTag TAG_CORRELATION_ID = new StringTag("message_bus.correlation_id");
    /**
     * An OpenTracing tag that contains the type of credentials used by a device.
     */
    public static final StringTag TAG_CREDENTIALS_TYPE = new StringTag("credentials_type");
    /**
     * An OpenTracing tag that contains the number of available credits for a sender link.
     */
    public static final IntTag TAG_CREDIT = new IntTag("message_bus.credit");
    /**
     * An OpenTracing tag that contains the device identifier.
     */
    public static final StringTag TAG_DEVICE_ID = new StringTag(MessageHelper.APP_PROPERTY_DEVICE_ID);
    /**
     * An OpenTracing tag that contains the time that a device will wait for an upstream message before disconnecting.
     */
    public static final IntTag TAG_DEVICE_TTD = new IntTag(CommandConstants.MSG_PROPERTY_DEVICE_TTD);
    /**
     * An OpenTracing tag that contains the gateway identifier.
     */
    public static final StringTag TAG_GATEWAY_ID = new StringTag(MessageHelper.APP_PROPERTY_GATEWAY_ID);
    /**
     * An OpenTracing tag that contains the identifier of a (request) message.
     */
    public static final StringTag TAG_MESSAGE_ID = new StringTag("message_bus.message_id");
    /**
     * An OpenTracing tag that indicates the container id of a remote peer.
     */
    public static final StringTag TAG_PEER_CONTAINER = new StringTag("peer.container");
    /**
     * An OpenTracing tag that contains the QoS that a device has used for publishing
     * a message.
     */
    public static final StringTag TAG_QOS = new StringTag("qos");
    /**
     * An OpenTracing tag that indicates the remote delivery state of an AMQP 1.0
     * message transfer.
     */
    public static final StringTag TAG_REMOTE_STATE = new StringTag("message_bus.remote_state");
    /**
     * An OpenTracing tag that contains the version of a resource, for example a record in a database.
     */
    public static final StringTag TAG_RESOURCE_VERSION = new StringTag("resource_version");
    /**
     * An OpenTracing tag that contains an X.500 Subject DN.
     */
    public static final StringTag TAG_SUBJECT_DN = new StringTag("subject_dn");
    /**
     * An OpenTracing tag that contains the tenant identifier.
     */
    public static final StringTag TAG_TENANT_ID = new StringTag(MessageHelper.APP_PROPERTY_TENANT_ID);
    /**
     * An OpenTracing tag indicating if a client's connection is secured using TLS.
     */
    public static final BooleanTag TAG_TLS = new BooleanTag("tls");
    /**
     * An OpenTracing span log entry field containing a nested exception object.
     */
    public static final String ERROR_CAUSE_OBJECT = "error.cause.object";

    private static final String JSON_KEY_SPAN_CONTEXT = "span-context";

    private static final Logger LOG = LoggerFactory.getLogger(TracingHelper.class);

    private TracingHelper() {
        // prevent instantiation
    }

    /**
     * Sets the tags identifying a device.
     *
     * @param span The span to set the tags on.
     * @param tenantId The tenant that the device belongs to or {@code null} if unknown.
     * @param deviceId The device identifier or {@code null} if unknown.
     */
    public static void setDeviceTags(final Span span, final String tenantId, final String deviceId) {
        setDeviceTags(span, tenantId, deviceId, null);
    }

    /**
     * Sets the tags identifying a device.
     *
     * @param span The span to set the tags on.
     * @param tenantId The tenant that the device belongs to or {@code null} if unknown.
     * @param deviceId The device identifier or {@code null} if unknown.
     * @param authId The authentication identifier or {@code null} if unknown.
     */
    public static void setDeviceTags(final Span span, final String tenantId, final String deviceId, final String authId) {
        if (span != null) {
            if (tenantId != null) {
                TAG_TENANT_ID.set(span, tenantId);
            }
            if (deviceId != null) {
                TAG_DEVICE_ID.set(span, deviceId);
            }
            if (authId != null) {
                TAG_AUTH_ID.set(span, authId);
            }
        }
    }

    /**
     * Creates a set of items to log for a message and an error.
     *
     * @param message The message.
     * @param error The error.
     * @return The items to log.
     */
    public static Map<String, Object> getErrorLogItems(final String message, final Throwable error) {
        final Map<String, Object> items = new HashMap<>(4);
        items.put(Fields.EVENT, Tags.ERROR.getKey());
        Optional.ofNullable(message)
                .ifPresent(ok -> items.put(Fields.MESSAGE, message));
        if (error != null) {
            items.put(Fields.ERROR_OBJECT, getErrorObjectFieldValue(error));
            if (error.getCause() != null) {
                items.put(ERROR_CAUSE_OBJECT, error.getCause());
            }
        }
        return items;
    }

    private static Object getErrorObjectFieldValue(final Throwable error) {
        if (error.getClass().getName().startsWith("org.eclipse.hono.")) {
            // skip inclusion of stacktrace for all Hono exceptions (e.g. ServiceInvocationException)
            // in order to reduce the size of the trace (stacktrace for these exceptions usually isn't really useful here)
            return error.toString();
        }
        return error;
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs a message.
     * <p>
     * This method does <em>not</em> finish the span.
     *
     * @param span The span to mark.
     * @param message The message to log on the span.
     * @throws NullPointerException if message is {@code null}.
     */
    public static void logError(final Span span, final String message) {
        Objects.requireNonNull(message);
        if (span != null) {
            final Map<String, String> items = new HashMap<>(2);
            items.put(Fields.MESSAGE, message);
            items.put(Fields.EVENT, Tags.ERROR.getKey());
            logError(span, items);
        }
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs an exception.
     * <p>
     * This method does <em>not</em> finish the span.
     * <p>
     * If the given error represents an unexpected error (e.g. a {@code NullPointerException}), a <em>WARN</em>
     * log entry will be created on the {@link Logger} of this class. To suppress this, the
     * {@link #logError(Span, String, Throwable, boolean)} method may be used instead, invoking it with {@code true}
     * as value for the {@code skipUnexpectedErrorCheck} parameter.
     *
     * @param span The span to mark.
     * @param error The exception that has occurred.
     * @throws NullPointerException if error is {@code null}.
     */
    public static void logError(final Span span, final Throwable error) {
        Objects.requireNonNull(error);
        logError(span, null, error, false);
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous, logs a message and an error.
     * <p>
     * This method does <em>not</em> finish the span.
     * <p>
     * If the given error represents an unexpected error (e.g. a {@code NullPointerException}), a <em>WARN</em>
     * log entry will be created on the {@link Logger} of this class. To suppress this, the
     * {@link #logError(Span, String, Throwable, boolean)} method may be used instead, invoking it with {@code true}
     * as value for the {@code skipUnexpectedErrorCheck} parameter.
     *
     * @param span The span to mark.
     * @param message The message to log on the span.
     * @param error The error to log on the span.
     * @throws NullPointerException if both message and error are {@code null}.
     */
    public static void logError(final Span span, final String message, final Throwable error) {
        logError(span, message, error, false);
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous, logs a message and an error.
     * <p>
     * This method does <em>not</em> finish the span.
     *
     * @param span The span to mark.
     * @param message The message to log on the span.
     * @param error The error to log on the span.
     * @param skipUnexpectedErrorCheck Whether to skip the check for an unexpected error, meaning no <em>WARN</em> log
     *            entry will be created for such an error.
     * @throws NullPointerException if both message and error are {@code null}.
     */
    public static void logError(final Span span, final String message, final Throwable error, final boolean skipUnexpectedErrorCheck) {
        if (message == null && error == null) {
            throw new NullPointerException("Either message or error must not be null");
        }
        if (!skipUnexpectedErrorCheck) {
            logUnexpectedError(error, span);
        }
        if (span != null) {
            logError(span, getErrorLogItems(message, error));
        }
    }

    private static void logUnexpectedError(final Throwable error, final Span span) {
        if (error instanceof NullPointerException
                || error instanceof IllegalArgumentException
                || error instanceof IllegalStateException) {
            Optional.ofNullable(span).ifPresentOrElse(
                    s -> LOG.warn("An unexpected error occurred! [logged on trace {}, span {}]",
                            s.context().toTraceId(), s.context().toSpanId(), error),
                    () -> LOG.warn("An unexpected error occurred!", error));
        }
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs several items.
     * <p>
     * This method does <em>not</em> finish the span.
     *
     * @param span The span to mark.
     * @param items The items to log on the span. Note that this method will
     *               also log an item using {@code event} as key and {@code error}
     *               as the value if the items do not contain such an entry already.
     *               A given {@code event} item with a different value will be ignored.
     */
    public static void logError(final Span span, final Map<String, ?> items) {
        if (span != null) {
            Tags.ERROR.set(span, Boolean.TRUE);
            if (items != null && !items.isEmpty()) {
                // ensure 'event' item is set and has value 'error'
                final Object event = items.get(Fields.EVENT);
                if (event == null || !Tags.ERROR.getKey().equals(event)) {
                    final HashMap<String, Object> itemsWithErrorEvent = new HashMap<>(items.size() + 1);
                    itemsWithErrorEvent.putAll(items);
                    itemsWithErrorEvent.put(Fields.EVENT, Tags.ERROR.getKey());
                    span.log(itemsWithErrorEvent);
                } else {
                    span.log(items);
                }
            } else {
                span.log(Tags.ERROR.getKey());
            }
        }
    }

    /**
     * Injects a {@code SpanContext} into a JSON object.
     * <p>
     * The span context will be injected into a new JSON object under key <em>span-context</em>.
     *
     * @param tracer The Tracer to use for injecting the context.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @param jsonObject The JSON object to inject the context into.
     * @throws NullPointerException if tracer or jsonObject is {@code null}.
     */
    public static void injectSpanContext(final Tracer tracer, final SpanContext spanContext, final JsonObject jsonObject) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(jsonObject);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            final JsonObject spanContextJson = new JsonObject();
            jsonObject.put(JSON_KEY_SPAN_CONTEXT, spanContextJson);
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP, new JsonObjectInjectAdapter(spanContextJson));
        }
    }

    /**
     * Extracts a {@code SpanContext} from a JSON object.
     * <p>
     * The span context will be read from a JSON object under key <em>span-context</em>.
     *
     * @param tracer The Tracer to use for extracting the context.
     * @param jsonObject The JSON object to extract the context from.
     * @return The context or {@code null} if the given JSON object does not contain a context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static SpanContext extractSpanContext(final Tracer tracer, final JsonObject jsonObject) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(jsonObject);

        final Object spanContextContainer = jsonObject.getValue(JSON_KEY_SPAN_CONTEXT);
        return spanContextContainer instanceof JsonObject
                ? tracer.extract(Format.Builtin.TEXT_MAP, new JsonObjectExtractAdapter((JsonObject) spanContextContainer))
                : null;
    }

    /**
     * Injects a {@code SpanContext} into the headers of vert.x {@code DeliveryOptions}.
     *
     * @param tracer The Tracer to use for injecting the context.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @param deliveryOptions The delivery options to inject the context into.
     * @throws NullPointerException if tracer or deliveryOptions is {@code null}.
     */
    public static void injectSpanContext(final Tracer tracer, final SpanContext spanContext, final DeliveryOptions deliveryOptions) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(deliveryOptions);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            final MultiMap headers = Optional.of(deliveryOptions)
                    .map(options -> options.getHeaders())
                    .orElseGet(() -> {
                        final MultiMap newHeaders = MultiMap.caseInsensitiveMultiMap();
                        deliveryOptions.setHeaders(newHeaders);
                        return newHeaders;
                    });
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP, new MultiMapInjectAdapter(headers));
        }
    }

    /**
     * Extracts a {@code SpanContext} from the headers of a vert.x event bus message.
     *
     * @param tracer The Tracer to use for extracting the context.
     * @param headers The headers to extract the context from.
     * @return The context or {@code null} if the given options do not contain a context.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static SpanContext extractSpanContext(final Tracer tracer, final MultiMap headers) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(headers);
        if (headers.isEmpty()) {
            return null;
        }
        final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, new MultiMapExtractAdapter(headers));
        if (spanContext != null) {
            // TracerShim returns current context instead of null if headers contain no context
            // see https://github.com/open-telemetry/opentelemetry-java/issues/4344
            final io.opentelemetry.api.trace.Span currentOpenTelemetrySpan = io.opentelemetry.api.trace.Span.current();
            if (currentOpenTelemetrySpan != null
                    && spanContext.toTraceId().equals(currentOpenTelemetrySpan.getSpanContext().getTraceId())
                    && spanContext.toSpanId().equals(currentOpenTelemetrySpan.getSpanContext().getSpanId())) {
                return null;
            }
        }
        return spanContext;
    }

    /**
     * Injects a {@code SpanContext} as key-value pairs into a given operation.
     * <p>
     * This provides a generic way to serialize a span context in any kind of textual data.
     * See {@link #extractSpanContext(Tracer, Supplier)} for the corresponding method to deserialize the
     * context from that data.
     *
     * @param tracer The Tracer to use for injecting the context.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @param keyValueConsumer The operation that will receive the key-value pairs representing the context.
     * @throws NullPointerException if tracer or keyValueConsumer is {@code null}.
     */
    public static void injectSpanContext(final Tracer tracer, final SpanContext spanContext,
            final BiConsumer<String, String> keyValueConsumer) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(keyValueConsumer);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP,
                    new TextMap() {
                        @Override
                        public Iterator<Map.Entry<String, String>> iterator() {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void put(final String key, final String value) {
                            keyValueConsumer.accept(key, value);
                        }
                    });
        }
    }

    /**
     * Extracts a {@code SpanContext} from given key-value pairs.
     * <p>
     * This provides a generic way to deserialize a span context from any kind of textual data.
     * See {@link #injectSpanContext(Tracer, SpanContext, BiConsumer)} for the corresponding method to
     * serialize the context in that data.
     *
     * @param tracer The Tracer to use for extracting the context.
     * @param keyValueIteratorSupplier The supplier that provides an iterator over key-values pairs representing the
     *            context.
     * @return The context or {@code null} if the given options do not contain a context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static SpanContext extractSpanContext(final Tracer tracer,
            final Supplier<Iterator<Map.Entry<String, String>>> keyValueIteratorSupplier) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(keyValueIteratorSupplier);

        return tracer.extract(Format.Builtin.TEXT_MAP, new TextMap() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return keyValueIteratorSupplier.get();
            }

            @Override
            public void put(final String key, final String value) {
                throw new UnsupportedOperationException();
            }
        });
    }

    /**
     * Creates a span builder that is initialized with the given operation name and a child-of reference to the given
     * span context (if set).
     * <p>
     * If the given span context contains a "sampling.priority" baggage item, it is set as a tag in the returned span
     * builder. Additionally, it is configured to ignore the active span.
     *
     * @param tracer The Tracer to use.
     * @param spanContext The span context that shall be the parent of the Span being built and that is used to derive
     *            the sampling priority from (may be null).
     * @param operationName The operation name to set for the span.
     * @param component The component to set for the span.
     * @return The span builder.
     * @throws NullPointerException if tracer or operationName is {@code null}.
     */
    public static Tracer.SpanBuilder buildChildSpan(final Tracer tracer, final SpanContext spanContext,
            final String operationName, final String component) {
        return buildSpan(tracer, spanContext, operationName, References.CHILD_OF)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), component);
    }

    /**
     * Creates a span builder that is initialized with the given operation name and a child-of reference to the given
     * span context (if set).
     * <p>
     * If the given span context contains a "sampling.priority" baggage item, it is set as a tag in the returned span
     * builder. Additionally, it is configured to ignore the active span and set kind tag to server.
     *
     * @param tracer The Tracer to use.
     * @param spanContext The span context that shall be the parent of the Span being built and that is used to derive
     *            the sampling priority from (may be null).
     * @param operationName The operation name to set for the span.
     * @param component The component to set for the span.
     * @return The span builder.
     * @throws NullPointerException if tracer or operationName is {@code null}.
     */
    public static Tracer.SpanBuilder buildServerChildSpan(final Tracer tracer, final SpanContext spanContext,
            final String operationName, final String component) {
        return buildChildSpan(tracer, spanContext, operationName, component)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
    }

    /**
     * Creates a span builder that is initialized with the given operation name and a follows-from reference to the
     * given span context (if set).
     * <p>
     * If the given span context contains a "sampling.priority" baggage item, it is set as a tag in the returned span
     * builder.
     *
     * @param tracer The Tracer to use.
     * @param spanContext The span context that the span being build shall have a follows-from reference to and that is
     *            used to derive the sampling priority from (may be null).
     * @param operationName The operation name to set for the span.
     * @return The span builder.
     * @throws NullPointerException if tracer or operationName is {@code null}.
     */
    public static Tracer.SpanBuilder buildFollowsFromSpan(final Tracer tracer, final SpanContext spanContext,
            final String operationName) {
        return buildSpan(tracer, spanContext, operationName, References.FOLLOWS_FROM);
    }

    /**
     * Creates a span builder that is initialized with the given operation name and a reference to the given span
     * context (if set).
     * <p>
     * If the given span context contains a "sampling.priority" baggage item, it is set as a tag in the returned span
     * builder.
     *
     * @param tracer The Tracer to use.
     * @param spanContext The span context to set as reference and to derive the sampling priority from (may be null).
     * @param operationName The operation name to set for the span
     * @param referenceType The type of reference towards the span context.
     * @return The span builder.
     * @throws NullPointerException if tracer or operationName or referenceType is {@code null}.
     */
    public static Tracer.SpanBuilder buildSpan(final Tracer tracer, final SpanContext spanContext,
            final String operationName, final String referenceType) {
        Objects.requireNonNull(tracer);
        Objects.requireNonNull(operationName);
        Objects.requireNonNull(referenceType);
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(operationName)
                .addReference(referenceType, spanContext);
        adoptSamplingPriorityFromContext(spanContext, spanBuilder);
        return spanBuilder;
    }

    /**
     * Sets a "sampling.priority" tag and baggage item with the given samplingPriority value in the given span.
     *
     * @param span The span to set the tag in.
     * @param samplingPriority The sampling priority to set.
     * @throws NullPointerException if the given span is null.
     */
    public static void setTraceSamplingPriority(final Span span, final int samplingPriority) {
        Objects.requireNonNull(span);
        Tags.SAMPLING_PRIORITY.set(span, samplingPriority);
        span.setBaggageItem(Tags.SAMPLING_PRIORITY.getKey(), Integer.toString(samplingPriority));
    }

    /**
     * Gets a "sampling.priority" baggage item from the given span context (if set) and
     * sets a corresponding tag in the given span builder.
     *
     * @param spanContext The span context (may be null).
     * @param spanBuilder The span builder to set the tag in.
     * @throws NullPointerException if the given spanBuilder is null.
     */
    public static void adoptSamplingPriorityFromContext(final SpanContext spanContext, final Tracer.SpanBuilder spanBuilder) {
        Objects.requireNonNull(spanBuilder);
        if (spanContext != null) {
            for (Map.Entry<String, String> baggageItem : spanContext.baggageItems()) {
                if (Tags.SAMPLING_PRIORITY.getKey().equals(baggageItem.getKey())) {
                    spanBuilder.withTag(Tags.SAMPLING_PRIORITY.getKey(), baggageItem.getValue());
                    break;
                }
            }
        }
    }
}
