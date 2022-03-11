/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingClient;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.kafka.client.producer.KafkaHeader;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;

/**
 * A client for publishing messages to a Kafka cluster.
 */
public abstract class AbstractKafkaBasedMessageSender implements MessagingClient, ServiceClient, Lifecycle {

    private static final String DEFAULT_SPAN_NAME = "send message";
    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Tracer tracer;

    private final MessagingKafkaProducerConfigProperties config;
    private final KafkaProducerFactory<String, Buffer> producerFactory;
    private final String producerName;

    private boolean stopped = false;
    private boolean producerCreated = false;

    /**
     * Creates a new Kafka-based message sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerName The producer name to use.
     * @param config The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractKafkaBasedMessageSender(
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final String producerName,
            final MessagingKafkaProducerConfigProperties config,
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

    @Override
    public final MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Starts the producer.
     */
    @Override
    public Future<Void> start() {
        stopped = false;

        return Future.succeededFuture()
                .map(v -> getOrCreateProducer()) // enclosed in map() to catch exceptions
                .onSuccess(v -> producerCreated = true)
                .recover(thr -> {
                    if (KafkaClientFactory.isRetriableClientCreationError(thr, config.getBootstrapServers())) {
                        // retry client creation in the background
                        getOrCreateProducerWithRetries()
                                .onSuccess(v -> producerCreated = true);
                        return Future.succeededFuture();
                    }
                    return Future.failedFuture(thr);
                })
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the producer.
     */
    @Override
    public Future<Void> stop() {
        stopped = true;
        return producerFactory.closeProducer(producerName);
    }

    /**
     * Sends a message to a Kafka broker and waits for the outcome.
     * <p>
     * This method encodes the given properties and then delegates to
     * {@link #sendAndWaitForOutcome(String, String, String, Buffer, List, Span)}.
     *
     * @param topic The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload The data to send or {@code null} if the message has no payload.
     * @param properties Additional meta data that should be included in the message.
     * @param currentSpan The <em>OpenTracing</em> span used to use for tracking the sending of the message.
     *             The span will <em>not</em> be finished by this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenantId, deviceId, properties or span are {@code null}.
     */
    protected final Future<Void> sendAndWaitForOutcome(
            final String topic,
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final Map<String, Object> properties,
            final Span currentSpan) {

        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(properties);
        Objects.requireNonNull(currentSpan);

        final List<KafkaHeader> headers = encodePropertiesAsKafkaHeaders(properties, currentSpan);
        return sendAndWaitForOutcome(topic, tenantId, deviceId, payload, headers, currentSpan);
    }

    /**
     * Sends a message to a Kafka broker and waits for the outcome.
     *
     * @param topic The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload The data to send or {@code null} if the message has no payload.
     * @param headers Additional meta data that should be included in the message.
     * @param currentSpan The <em>OpenTracing</em> span used to use for tracking the sending of the message.
     *             The span will <em>not</em> be finished by this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenantId, deviceId, headers or span are {@code null}.
     */
    protected final Future<Void> sendAndWaitForOutcome(
            final String topic,
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final List<KafkaHeader> headers,
            final Span currentSpan) {

        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(currentSpan);

        if (stopped) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "sender already stopped"));
        }
        final KafkaProducerRecord<String, Buffer> record = KafkaProducerRecord.create(topic, deviceId, payload);

        log.trace("sending message to Kafka [topic: {}, tenantId: {}, deviceId: {}]", topic, tenantId, deviceId);
        record.addHeaders(headers);
        KafkaTracingHelper.injectSpanContext(tracer, record, currentSpan.context());
        logProducerRecord(currentSpan, record);

        return getOrCreateProducer().send(record)
                .onSuccess(recordMetadata -> logRecordMetadata(currentSpan, deviceId, recordMetadata))
                .otherwise(t -> {
                    logError(currentSpan, topic, tenantId, deviceId, t);
                    throw new ServerErrorException(tenantId, getErrorCode(t), t);
                })
                .mapEmpty();
    }

    private KafkaProducer<String, Buffer> getOrCreateProducer() {
        return producerFactory.getOrCreateProducer(producerName, config);
    }

    private Future<KafkaProducer<String, Buffer>> getOrCreateProducerWithRetries() {
        return producerFactory.getOrCreateProducerWithRetries(producerName, config,
                KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Registers a procedure for checking if this client's initial Kafka client creation succeeded.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        // verify that client creation succeeded
        readinessHandler.register(
                String.format("%s-kafka-client-creation-%s", producerName, UUID.randomUUID()),
                status -> status.tryComplete(producerCreated ? Status.OK() : Status.KO()));
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // no liveness checks to be added
    }

    /**
     * Encodes the given properties as a list of Kafka record headers.
     *
     * @param properties The properties to encode.
     * @param span The span to log to if there are exceptions encoding the properties.
     * @return The created header list.
     */
    private List<KafkaHeader> encodePropertiesAsKafkaHeaders(final Map<String, Object> properties, final Span span) {
        final List<KafkaHeader> headers = new ArrayList<>();

        properties.forEach((k, v) -> {
            try {
                headers.add(KafkaRecordHelper.createKafkaHeader(k, v));
            } catch (final EncodeException e) {
                log.info("failed to serialize property with key [{}] to Kafka header", k);
                span.log("failed to create Kafka header from property: " + k);
            }
        });

        if (!properties.containsKey(MessageHelper.SYS_PROPERTY_CREATION_TIME)) {
            // must match http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-types-v1.0-os.html#type-timestamp
            // as defined in https://www.eclipse.org/hono/docs/api/telemetry/#forward-telemetry-data
            headers.add(KafkaRecordHelper.createKafkaHeader(
                    MessageHelper.SYS_PROPERTY_CREATION_TIME,
                    Json.encode(Instant.now().toEpochMilli())));
        }

        return headers;
    }

    /**
     * Creates a new <em>OpenTracing</em> child span to trace producing messages to Kafka.
     *
     * @param operationName The operation name to set for the span.
     *                       If {@code null}, "send message" will be used.
     * @param topic The topic to which the message is sent.
     * @param tenantId The tenant identifier related to the operation.
     * @param deviceId The device identifier related to the operation.
     * @param context The span context to set as parent and to derive the sampling priority from (may be null).
     * @return The new span.
     * @throws NullPointerException if tracer or topic is {@code null}.
     */
    protected Span startChildSpan(final String operationName, final String topic, final String tenantId, final String deviceId,
            final SpanContext context) {
        return startSpan(operationName, topic, tenantId, deviceId, References.CHILD_OF, context);
    }

    /**
     * Creates a new <em>OpenTracing</em> span to trace producing messages to Kafka.
     *
     * @param operationName The operation name to set for the span.
     *                       If {@code null}, "send message" will be used.
     * @param topic The topic to which the message is sent.
     * @param tenantId The tenant identifier related to the operation.
     * @param deviceId The device identifier related to the operation.
     * @param referenceType The type of reference towards the given span context.
     * @param context The span context to set as parent and to derive the sampling priority from (may be null).
     * @return The new span.
     * @throws NullPointerException if tracer or topic is {@code null}.
     */
    protected Span startSpan(final String operationName, final String topic, final String tenantId, final String deviceId,
            final String referenceType, final SpanContext context) {
        final String operationNameToUse = Strings.isNullOrEmpty(operationName) ? DEFAULT_SPAN_NAME : operationName;
        return KafkaTracingHelper.newProducerSpan(tracer, operationNameToUse, topic, referenceType, context)
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

    private void logError(
            final Span span,
            final String topic,
            final String tenantId,
            final String deviceId,
            final Throwable cause) {

        log.debug("sending message failed [topic: {}, key: {}, tenantId: {}, deviceId: {}]",
                topic, deviceId, tenantId, deviceId, cause);

        Tags.HTTP_STATUS.set(span, getErrorCode(cause));
        TracingHelper.logError(span, cause);
    }

    private int getErrorCode(final Throwable t) {
        /*
         * TODO set error code depending on type of exception?
         *
         * terminal problems (the message will never be sent):
         *
         * InvalidTopicException
         * OffsetMetadataTooLargeException
         * RecordBatchTooLargeException
         * RecordTooLargeException
         * UnknownServerException
         * UnknownProducerIdException
         *
         * transient problems (may be covered by increasing #.retries):
         *
         * CorruptRecordException
         * InvalidMetadataException
         * NotEnoughReplicasAfterAppendException
         * NotEnoughReplicasException
         * OffsetOutOfRangeException
         * TimeoutException
         * UnknownTopicOrPartitionException
         */

        return HttpURLConnection.HTTP_UNAVAILABLE;
    }
}
