/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.tracing;

import java.util.Objects;

import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.Tags;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;

/**
 * A helper class providing Kafka-specific utility methods for interacting with the OpenTracing API.
 *
 */
public final class KafkaTracingHelper {

    /**
     * An OpenTracing tag that contains the offset of a Kafka record.
     */
    public static final LongTag TAG_OFFSET = new LongTag("offset");

    /**
     * An OpenTracing tag that contains the partition of a Kafka record.
     */
    public static final IntTag TAG_PARTITION = new IntTag("partition");

    /**
     * An OpenTracing tag that contains the timestamp of a Kafka record.
     */
    public static final LongTag TAG_TIMESTAMP = new LongTag("timestamp");

    private KafkaTracingHelper() {
        // prevent instantiation
    }

    /**
     * Creates a new <em>OpenTracing</em> span to trace producing messages to Kafka.
     * <p>
     * The returned span will already contain the following tags:
     * <ul>
     * <li>{@link Tags#COMPONENT} - set to <em>hono-client-kafka</em></li>
     * <li>{@link Tags#MESSAGE_BUS_DESTINATION} - set to {@code To_<topic>}</li>
     * <li>{@link Tags#SPAN_KIND} - set to {@link Tags#SPAN_KIND_PRODUCER}</li>
     * <li>{@link Tags#PEER_SERVICE} - set to <em>kafka</em></li>
     * </ul>
     *
     * @param tracer The Tracer to use.
     * @param operationName The operation name to set for the span.
     * @param topic The topic to which the message is sent.
     * @param referenceType The type of reference towards the span context.
     * @param parent The span context to set as parent and to derive the sampling priority from (may be null).
     * @return The new span.
     * @throws NullPointerException if tracer, operationName or topic is {@code null}.
     */
    public static Span newProducerSpan(final Tracer tracer, final String operationName, final String topic,
            final String referenceType, final SpanContext parent) {
        Objects.requireNonNull(tracer);
        Objects.requireNonNull(operationName);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(referenceType);

        return TracingHelper.buildSpan(tracer, parent, operationName, referenceType)
                .ignoreActiveSpan()
                .withTag(Tags.COMPONENT.getKey(), "hono-client-kafka")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER)
                .withTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), topic)
                .withTag(Tags.PEER_SERVICE.getKey(), "kafka")
                .start();
    }

    /**
     * Sets tags from record metadata.
     * <p>
     * It sets the following tags:
     * <ul>
     * <li>{@link #TAG_OFFSET}</li>
     * <li>{@link #TAG_PARTITION}</li>
     * <li>{@link #TAG_TIMESTAMP}</li>
     * </ul>
     * <p>
     * <em>It does not set the topic, as this is expected to be already already set.</em>
     *
     * @param span The span to set the tags on.
     * @param recordMetadata The record metadata.
     */
    public static void setRecordMetadataTags(final Span span, final RecordMetadata recordMetadata) {

        TAG_OFFSET.set(span, recordMetadata.getOffset());
        TAG_PARTITION.set(span, recordMetadata.getPartition());
        TAG_TIMESTAMP.set(span, recordMetadata.getTimestamp());
    }

    /**
     * Sets tags from a consumer record.
     * <p>
     * It sets the following tags:
     * <ul>
     * <li>{@link Tags#MESSAGE_BUS_DESTINATION}</li>
     * <li>{@link #TAG_OFFSET}</li>
     * <li>{@link #TAG_PARTITION}</li>
     * <li>{@link #TAG_TIMESTAMP}</li>
     * </ul>
     *
     * @param span The span to set the tags on.
     * @param record The record.
     */
    public static void setRecordTags(final Span span, final KafkaConsumerRecord<?, ?> record) {

        Tags.MESSAGE_BUS_DESTINATION.set(span, record.topic());
        TAG_OFFSET.set(span, record.offset());
        TAG_PARTITION.set(span, record.partition());
        TAG_TIMESTAMP.set(span, record.timestamp());
    }

    /**
     * Injects a {@code SpanContext} into a Kafka record.
     * <p>
     * The span context will be written to the record headers.
     *
     * @param <V> The type of the record's value.
     * @param tracer The Tracer to use for injecting the context.
     * @param record The Kafka record to inject the context into.
     * @param spanContext The context to inject or {@code null} if no context is available.
     * @throws NullPointerException if tracer or record is {@code null}.
     */
    public static <V> void injectSpanContext(final Tracer tracer, final KafkaProducerRecord<String, V> record,
            final SpanContext spanContext) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(record);

        if (spanContext != null && !(spanContext instanceof NoopSpanContext)) {
            tracer.inject(spanContext, Format.Builtin.TEXT_MAP, new KafkaHeadersInjectAdapter(record.headers()));
        }
    }

    /**
     * Extracts a {@code SpanContext} from a Kafka record.
     * <p>
     * The span context will be read from the record headers.
     *
     * @param <V> The type of the record's value.
     * @param tracer The Tracer to use for extracting the context.
     * @param record The Kafka record to extract the context from.
     * @return The context or {@code null} if the given Kafka record does not contain a context.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static <V> SpanContext extractSpanContext(final Tracer tracer, final KafkaConsumerRecord<String, V> record) {

        Objects.requireNonNull(tracer);
        Objects.requireNonNull(record);

        return tracer.extract(Format.Builtin.TEXT_MAP, new KafkaHeadersExtractAdapter(record.headers()));
    }
}
