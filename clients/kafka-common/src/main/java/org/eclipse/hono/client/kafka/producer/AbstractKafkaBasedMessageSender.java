/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.kafka.producer;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import io.vertx.kafka.client.producer.impl.KafkaHeaderImpl;

/**
 * A client for publishing messages to a Kafka cluster.
 */
public abstract class AbstractKafkaBasedMessageSender implements Lifecycle {
    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Tracer tracer;

    private final Map<String, String> config;
    private final KafkaProducerFactory<String, Buffer> producerFactory;
    private final String producerName;

    /**
     * Creates a new Kafka-based message sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerName The producer name to use.
     * @param config The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractKafkaBasedMessageSender(final KafkaProducerFactory<String, Buffer> producerFactory,
            final String producerName,
            final Map<String, String> config,
            final Tracer tracer) {
        Objects.requireNonNull(producerFactory);
        Objects.requireNonNull(producerName);
        Objects.requireNonNull(config);
        Objects.requireNonNull(tracer);

        this.producerFactory = producerFactory;
        this.producerName = producerName;
        this.config = config;
        this.tracer = tracer;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Starts the producer.
     */
    @Override
    public Future<Void> start() {
        getOrCreateProducer();
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the producer.
     */
    @Override
    public Future<Void> stop() {
        return producerFactory.closeProducer(producerName);
    }

    /**
     * Sends a message to a kafka cluster and doesn't wait for an outcome.
     *
     * @param topic The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload The data to send.
     * @param properties Additional meta data that should be included in the message.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @throws NullPointerException if topic, tenantId, deviceId or properties are {@code null}.
     */
    protected void send(final String topic, final String tenantId, final String deviceId, final Buffer payload,
            final Map<String, Object> properties, final SpanContext context) {

        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(properties);

        final Span span = startSpan(topic, tenantId, deviceId, References.FOLLOWS_FROM, context);
        final List<KafkaHeader> headers = encodePropertiesAsKafkaHeaders(properties, span);
        send(topic, tenantId, deviceId, payload, headers, span);
    }

    /**
     * Sends a message to a kafka cluster and doesn't wait for an outcome.
     *
     * @param topic The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload The data to send.
     * @param headers Additional meta data that should be included in the message.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @throws NullPointerException if topic, tenantId, deviceId or headers are {@code null}.
     */
    protected void send(final String topic, final String tenantId, final String deviceId, final Buffer payload,
            final List<KafkaHeader> headers, final SpanContext context) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(headers);

        final Span span = startSpan(topic, tenantId, deviceId, References.FOLLOWS_FROM, context);
        send(topic, tenantId, deviceId, payload, headers, span);
    }

    /**
     * Sends a message to a kafka cluster and waits for the outcome.
     *
     * @param topic The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload The data to send.
     * @param properties Additional meta data that should be included in the message.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenantId, deviceId or properties are {@code null}.
     */
    protected Future<Void> sendAndWaitForOutcome(final String topic, final String tenantId, final String deviceId,
            final Buffer payload, final Map<String, Object> properties, final SpanContext context) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(properties);

        final Span span = startSpan(topic, tenantId, deviceId, References.CHILD_OF, context);
        final List<KafkaHeader> headers = encodePropertiesAsKafkaHeaders(properties, span);
        return send(topic, tenantId, deviceId, payload, headers, span);
    }

    /**
     * Sends a message to a kafka cluster and waits for the outcome.
     *
     * @param topic The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload The data to send.
     * @param headers Additional meta data that should be included in the message.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenantId, deviceId or headers are {@code null}.
     */
    protected Future<Void> sendAndWaitForOutcome(final String topic, final String tenantId, final String deviceId,
            final Buffer payload, final List<KafkaHeader> headers, final SpanContext context) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(headers);

        final Span span = startSpan(topic, tenantId, deviceId, References.CHILD_OF, context);
        return send(topic, tenantId, deviceId, payload, headers, span);
    }

    private Future<Void> send(final String topic, final String tenantId, final String deviceId, final Buffer payload,
            final List<KafkaHeader> headers, final Span span) {
        final KafkaProducerRecord<String, Buffer> record = KafkaProducerRecord.create(topic, deviceId, payload);
        final Promise<RecordMetadata> sendPromise = Promise.promise();

        log.trace("sending message to Kafka [topic: {}, tenantId: {}, deviceId: {}]", topic, tenantId, deviceId);
        record.addHeaders(headers);
        KafkaTracingHelper.injectSpanContext(tracer, record, span.context());
        logProducerRecord(span, record);
        getOrCreateProducer().send(record, sendPromise);

        return sendPromise.future()
                .recover(t -> {
                    logError(span, topic, tenantId, deviceId, t);
                    span.finish();
                    return Future.failedFuture(new ServerErrorException(getErrorCode(t), t));
                })
                .map(recordMetadata -> {
                    logRecordMetadata(span, deviceId, recordMetadata);
                    span.finish();
                    return null;
                });
    }

    private KafkaProducer<String, Buffer> getOrCreateProducer() {
        return producerFactory.getOrCreateProducer(producerName, config);
    }

    private List<KafkaHeader> encodePropertiesAsKafkaHeaders(final Map<String, Object> properties, final Span span) {
        final List<KafkaHeader> headers = new ArrayList<>();

            properties.forEach((k, v) -> {
                try {
                    final Buffer headerValue = (v instanceof String)
                            ? Buffer.buffer((String) v)
                            : Buffer.buffer(Json.encode(v));

                    headers.add(new KafkaHeaderImpl(k, headerValue));
                } catch (final EncodeException e) {
                    log.info("failed to serialize property with key [{}] to Kafka header", k);
                    span.log("failed to create Kafka header from property: " + k);
                }
            });

        return headers;
    }

    private Span startSpan(final String topic, final String tenantId, final String deviceId, final String referenceType,
            final SpanContext context) {
        return KafkaTracingHelper.newProducerSpan(tracer, topic, referenceType, context)
                .setTag(TracingHelper.TAG_TENANT_ID.getKey(), tenantId)
                .setTag(TracingHelper.TAG_DEVICE_ID.getKey(), deviceId);
    }

    private void logProducerRecord(final Span span, final KafkaProducerRecord<String, Buffer> record) {
        final String headersAsString = record.headers()
                .stream()
                .map(header -> header.key() + "=" + header.value())
                .collect(Collectors.joining(",", "{", "}"));

        log.trace("producing message [topic: {}, key: {}, partition: {}, timestamp: {}, headers: {}]",
                record.topic(), record.key(), record.partition(), record.timestamp(), headersAsString);

        span.log("producing message with headers: " + headersAsString);
    }

    private void logRecordMetadata(final Span span, final String recordKey, final RecordMetadata metadata) {

        log.trace("message produced to Kafka [topic: {}, key: {}, partition: {}, offset: {}, timestamp: {}]",
                metadata.getTopic(), recordKey, metadata.getPartition(), metadata.getOffset(), metadata.getTimestamp());

        span.log("message produced to Kafka");
        KafkaTracingHelper.setRecordMetadataTags(span, metadata);
        Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_ACCEPTED);

    }

    private void logError(final Span span, final String topic, final String tenantId, final String deviceId,
            final Throwable cause) {
        log.debug("sending message failed [topic: {}, key: {}, tenantId: {}, deviceId: {}]", topic, deviceId, tenantId,
                deviceId, cause);

        Tags.HTTP_STATUS.set(span, getErrorCode(cause));
        TracingHelper.logError(span, cause);
    }

    private int getErrorCode(final Throwable t) {
        /*
         * TODO set error code depending on exception?
         *
         * Possible thrown exceptions include:
         *
         * Non-Retriable exceptions (fatal, the message will never be sent):
         *
         * InvalidTopicException OffsetMetadataTooLargeException RecordBatchTooLargeException RecordTooLargeException
         * UnknownServerException UnknownProducerIdException
         *
         * Retriable exceptions (transient, may be covered by increasing #.retries):
         *
         * CorruptRecordException InvalidMetadataException NotEnoughReplicasAfterAppendException
         * NotEnoughReplicasException OffsetOutOfRangeException TimeoutException UnknownTopicOrPartitionException
         */

        return HttpURLConnection.HTTP_UNAVAILABLE;
    }
}
