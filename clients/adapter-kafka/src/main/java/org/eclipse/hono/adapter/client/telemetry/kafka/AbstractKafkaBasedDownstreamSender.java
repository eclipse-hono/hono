/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.telemetry.kafka;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.client.KafkaProducerFactory;
import org.eclipse.hono.kafka.client.tracing.KafkaTracingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
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
public abstract class AbstractKafkaBasedDownstreamSender implements Lifecycle {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final KafkaProducerFactory<String, Buffer> producerFactory;
    private final String producerName;
    private final Map<String, String> config;
    private final ProtocolAdapterProperties adapterConfig;
    private final Tracer tracer;

    /**
     * Creates a new Kafka-based telemetry sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerName The producer name to use.
     * @param config The Kafka producer configuration properties to use.
     * @param adapterConfig The protocol adapter's configuration properties.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */

    public AbstractKafkaBasedDownstreamSender(final KafkaProducerFactory<String, Buffer> producerFactory,
            final String producerName, final Map<String, String> config, final ProtocolAdapterProperties adapterConfig,
            final Tracer tracer) {

        Objects.requireNonNull(producerFactory);
        Objects.requireNonNull(producerName);
        Objects.requireNonNull(config);
        Objects.requireNonNull(adapterConfig);
        Objects.requireNonNull(tracer);

        this.producerFactory = producerFactory;
        this.producerName = producerName;
        this.config = config;
        this.adapterConfig = adapterConfig;
        this.tracer = tracer;
    }

    /**
     * Sends a message downstream.
     * <p>
     * Default properties defined either at the device or tenant level are added to the message headers.
     *
     * @param topic The topic to send the message to.
     * @param tenant The tenant that the device belongs to.
     * @param device The registration assertion for the device that the data originates from.
     * @param qos The delivery semantics to use for sending the data.
     * @param contentType The content type of the data. If {@code null}, the content type be taken from the following
     *            sources (in that order, the first one that is present is used):
     *            <ol>
     *            <li>the <em>contentType</em> parameter</li>
     *            <li>the property with key {@link org.eclipse.hono.util.MessageHelper#SYS_PROPERTY_CONTENT_TYPE} in the
     *            <em>properties</em> parameter</li>
     *            <li>the device default</li>
     *            <li>the tenant default</li>
     *            <li>the {@linkplain org.eclipse.hono.util.MessageHelper#CONTENT_TYPE_OCTET_STREAM default content
     *            type}</li>
     *            </ol>
     * @param payload The data to send.
     * @param properties Additional meta data that should be included in the downstream message.
     * @param context The currently active OpenTracing span (may be {@code null}). An implementation should use this as
     *            the parent for any span it creates for tracing the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the message has been sent downstream.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     *         not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenant, device, or qos are {@code null}.
     */
    protected Future<Void> send(final HonoTopic topic, final TenantObject tenant, final RegistrationAssertion device,
            final QoS qos, final String contentType, final Buffer payload, final Map<String, Object> properties,
            final SpanContext context) {

        Objects.requireNonNull(topic);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(device);
        Objects.requireNonNull(qos);

        final String tenantId = tenant.getTenantId();
        final String deviceId = device.getDeviceId();

        log.trace("sending to Kafka [topic: {}, tenantId: {}, deviceId: {}, qos: {}, contentType: {}, properties: {}]",
                topic, tenantId, deviceId, qos, contentType, properties);
        final Span span = startSpan(topic, tenantId, deviceId, qos, contentType, context);

        final KafkaProducerRecord<String, Buffer> record = KafkaProducerRecord.create(topic.toString(), deviceId,
                payload);

        final Map<String, Object> propsWithDefaults = addDefaults(tenant, device, qos, contentType, properties);
        record.addHeaders(encodePropertiesAsKafkaHeaders(propsWithDefaults, span));

        KafkaTracingHelper.injectSpanContext(tracer, record, span.context());
        logProducerRecord(span, record);

        final Promise<RecordMetadata> promise = Promise.promise();
        getOrCreateProducer().send(record, promise);

        final Future<Void> producerFuture = promise.future()
                .recover(t -> {
                    logError(span, topic, tenantId, deviceId, qos, t);
                    span.finish();
                    return Future.failedFuture(new ServerErrorException(getErrorCode(t), t));
                })
                .map(recordMetadata -> {
                    logRecordMetadata(span, deviceId, recordMetadata);
                    span.finish();
                    return null;
                });

        return qos.equals(QoS.AT_MOST_ONCE) ? Future.succeededFuture() : producerFuture;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Starts the producer.
     */
    @Override
    public Future<Void> start() {
        configureUniqueClientId();
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

    private KafkaProducer<String, Buffer> getOrCreateProducer() {
        return producerFactory.getOrCreateProducer(producerName, config);
    }

    private Map<String, Object> addDefaults(final TenantObject tenant, final RegistrationAssertion device,
            final QoS qos, final String contentType, final Map<String, Object> properties) {

        final Map<String, Object> headerProperties = new HashMap<>();
        if (adapterConfig.isDefaultsEnabled()) {
            headerProperties.putAll(tenant.getDefaults().copy().getMap()); // (1) add tenant defaults
            headerProperties.putAll(device.getDefaults()); // (2) overwrite with device defaults
        }

        // (3) overwrite with properties provided by protocol adapter
        Optional.ofNullable(properties).ifPresent(headerProperties::putAll);

        // (4) overwrite by values of separate parameters
        headerProperties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, device.getDeviceId());
        headerProperties.put(MessageHelper.APP_PROPERTY_QOS, qos.ordinal());
        if (contentType != null) {
            headerProperties.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, contentType);
        }

        // (5) if still no content type present, set the default content type
        headerProperties.putIfAbsent(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, MessageHelper.CONTENT_TYPE_OCTET_STREAM);

        if (headerProperties.containsKey(MessageHelper.APP_PROPERTY_DEVICE_TTD)
                && !headerProperties.containsKey(MessageHelper.SYS_PROPERTY_CREATION_TIME)) {
            // TODO set this as creation time in the KafkaRecord?

            // must match http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-types-v1.0-os.html#type-timestamp
            // as defined in https://www.eclipse.org/hono/docs/api/telemetry/#forward-telemetry-data
            final long timestamp = Instant.now().toEpochMilli();
            headerProperties.put(MessageHelper.SYS_PROPERTY_CREATION_TIME, timestamp);
        }

        return headerProperties;
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

    private Span startSpan(final HonoTopic topic, final String tenantId, final String deviceId, final QoS qos,
            final String contentType, final SpanContext context) {

        final String referenceType = QoS.AT_MOST_ONCE.equals(qos) ? References.FOLLOWS_FROM : References.CHILD_OF;
        return KafkaTracingHelper.newProducerSpan(tracer, topic, referenceType, context)
                .setTag(TracingHelper.TAG_TENANT_ID.getKey(), tenantId)
                .setTag(TracingHelper.TAG_DEVICE_ID.getKey(), deviceId)
                .setTag(TracingHelper.TAG_QOS.getKey(), qos.name())
                .setTag(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, contentType);
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

    private void logError(final Span span, final HonoTopic topic, final String tenantId, final String deviceId,
            final QoS qos, final Throwable cause) {
        log.debug("sending message failed [topic: {}, key: {}, qos: {}, tenantId: {}, deviceId: {}]",
                topic, deviceId, qos, tenantId, deviceId, cause);

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

    private void configureUniqueClientId() {
        final UUID uuid = UUID.randomUUID();

        final String clientId = config.get(ProducerConfig.CLIENT_ID_CONFIG);
        if (clientId == null) {
            config.put(ProducerConfig.CLIENT_ID_CONFIG, String.format("%s-%s", producerName, uuid));
        } else {
            config.put(ProducerConfig.CLIENT_ID_CONFIG, String.format("%s-%s-%s", clientId, producerName, uuid));
        }
    }
}
