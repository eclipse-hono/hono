/*
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

package org.eclipse.hono.client.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Utility methods for working with Kafka {@code Message}s.
 */
public final class KafkaRecordHelper {

    /**
     * Prefix to use for marking properties of command messages that should be included in response messages indicating
     * failure to deliver the command.
     */
    public static final String DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX = "delivery-failure-notification-metadata";

    /**
     * The name of the boolean Kafka record header that defines whether a response is required for the command.
     */
    public static final String HEADER_RESPONSE_REQUIRED = "response-required";
    /**
     * The name of the Integer Kafka record header that contains the index of the tenant topic partition
     * that a command record was originally stored in.
     */
    public static final String HEADER_ORIGINAL_PARTITION = "orig-partition";
    /**
     * The name of the Long Kafka record header that contains the offset in the tenant topic partition
     * that a command record was originally stored in.
     */
    public static final String HEADER_ORIGINAL_OFFSET = "orig-offset";

    private KafkaRecordHelper() {
    }

    /**
     * Creates a Kafka header for the given key and value.
     * <p>
     * If the value is not a {@code String}, it will be JSON encoded.
     *
     * @param key The key of the header.
     * @param value The value of the header.
     * @return The encoded Kafka header.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws EncodeException if encoding the value to JSON fails.
     */
    public static KafkaHeader createKafkaHeader(final String key, final Object value) throws EncodeException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        final String encodedValue;
        if (value instanceof String) {
            encodedValue = (String) value;
        } else {
            encodedValue = Json.encode(value);
        }

        return KafkaHeader.header(key, Buffer.buffer(encodedValue));
    }

    /**
     * Gets the {@link MessageHelper#SYS_PROPERTY_CONTENT_TYPE content type} header from the given list of Kafka
     * headers.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first
     * occurrence is returned.
     *
     * @param headers The headers to get the content-type from.
     * @return The content type (may be empty).
     */
    public static Optional<String> getContentType(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.SYS_PROPERTY_CONTENT_TYPE, String.class);
    }

    /**
     * Gets the {@link MessageHelper#APP_PROPERTY_QOS quality of service} header from the given list of Kafka headers.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first
     * occurrence is returned.
     *
     * @param headers The headers to get the QoS from.
     * @return The quality-of-service level (may be empty).
     */
    public static Optional<QoS> getQoS(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.APP_PROPERTY_QOS, Integer.class)
                .map(integer -> Integer.valueOf(0).equals(integer) ? QoS.AT_MOST_ONCE : QoS.AT_LEAST_ONCE);
    }

    /**
     * Checks if a {@link MessageHelper#SYS_HEADER_PROPERTY_TTL ttl} header is present and the time already elapsed.
     *
     * @param headers The headers to be checked.
     * @return {@code true} if <em>ttl</em> and <em>creation-time</em> headers are present and the time-to-live is
     *         already elapsed, {@code false} otherwise.
     */
    public static boolean isTtlElapsed(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.SYS_HEADER_PROPERTY_TTL, Long.class)
                .map(ttl -> {
                    final Instant now = Instant.now();
                    final Instant elapseTime = getCreationTime(headers).orElse(now).plus(Duration.ofMillis(ttl));
                    return elapseTime.isBefore(now);
                })
                .orElse(Boolean.FALSE);
    }

    /**
     * Gets the point in time represented by the value of the {@value MessageHelper#SYS_PROPERTY_CREATION_TIME}
     * header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is used.
     *
     * @param headers The headers to get the creation time from.
     * @return The point in time.
     */
    public static Optional<Instant> getCreationTime(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.SYS_PROPERTY_CREATION_TIME, Long.class).map(Instant::ofEpochMilli);
    }

    /**
     * Gets the value of the {@value MessageHelper#APP_PROPERTY_TENANT_ID} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<String> getTenantId(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.APP_PROPERTY_TENANT_ID, String.class);
    }

    /**
     * Creates a {@value MessageHelper#APP_PROPERTY_TENANT_ID} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createTenantIdHeader(final String value) {
        return createKafkaHeader(MessageHelper.APP_PROPERTY_TENANT_ID, value);
    }

    /**
     * Gets the value of the {@value MessageHelper#APP_PROPERTY_DEVICE_ID} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<String> getDeviceId(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.APP_PROPERTY_DEVICE_ID, String.class);
    }

    /**
     * Creates a {@value MessageHelper#APP_PROPERTY_DEVICE_ID} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createDeviceIdHeader(final String value) {
        return createKafkaHeader(MessageHelper.APP_PROPERTY_DEVICE_ID, value);
    }

    /**
     * Gets the value of the {@value MessageHelper#SYS_PROPERTY_SUBJECT} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<String> getSubject(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.SYS_PROPERTY_SUBJECT, String.class);
    }

    /**
     * Creates a {@value MessageHelper#SYS_PROPERTY_SUBJECT} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createSubjectHeader(final String value) {
        return createKafkaHeader(MessageHelper.SYS_PROPERTY_SUBJECT, value);
    }

    /**
     * Gets the value of the {@value MessageHelper#SYS_PROPERTY_CORRELATION_ID} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<String> getCorrelationId(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.SYS_PROPERTY_CORRELATION_ID, String.class);
    }

    /**
     * Creates a {@value MessageHelper#SYS_PROPERTY_CORRELATION_ID} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createCorrelationIdHeader(final String value) {
        return createKafkaHeader(MessageHelper.SYS_PROPERTY_CORRELATION_ID, value);
    }

    /**
     * Gets the value of the {@value MessageHelper#APP_PROPERTY_CMD_VIA} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<String> getViaHeader(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, MessageHelper.APP_PROPERTY_CMD_VIA, String.class);
    }

    /**
     * Creates a {@value MessageHelper#APP_PROPERTY_CMD_VIA} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createViaHeader(final String value) {
        return createKafkaHeader(MessageHelper.APP_PROPERTY_CMD_VIA, value);
    }

    /**
     * Gets the value of the {@value #HEADER_ORIGINAL_PARTITION} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<Integer> getOriginalPartitionHeader(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, HEADER_ORIGINAL_PARTITION, Integer.class);
    }

    /**
     * Creates a {@value #HEADER_ORIGINAL_PARTITION} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createOriginalPartitionHeader(final int value) {
        return createKafkaHeader(HEADER_ORIGINAL_PARTITION, value);
    }

    /**
     * Gets the value of the {@value #HEADER_ORIGINAL_OFFSET} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value.
     */
    public static Optional<Long> getOriginalOffsetHeader(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, HEADER_ORIGINAL_OFFSET, Long.class);
    }

    /**
     * Creates a {@value #HEADER_ORIGINAL_OFFSET} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createOriginalOffsetHeader(final long value) {
        return createKafkaHeader(HEADER_ORIGINAL_OFFSET, value);
    }

    /**
     * Gets the value of the {@value #HEADER_RESPONSE_REQUIRED} header.
     * <p>
     * If the list contains multiple occurrences of the header, the value of its first occurrence is returned.
     *
     * @param headers The headers to get the value from.
     * @return The header value or {@code false} if the list does not contain the header.
     */
    public static boolean isResponseRequired(final List<KafkaHeader> headers) {
        return getHeaderValue(headers, HEADER_RESPONSE_REQUIRED, Boolean.class).orElse(false);
    }

    /**
     * Creates a {@value #HEADER_RESPONSE_REQUIRED} header for a value.
     *
     * @param value The header value to set.
     * @return The header.
     */
    public static KafkaHeader createResponseRequiredHeader(final boolean value) {
        return createKafkaHeader(HEADER_RESPONSE_REQUIRED, value);
    }

    /**
     * Gets the value of a Kafka header from the given headers.
     * <p>
     * If the headers contain multiple occurrences of the same key, the value of its first
     * occurrence is returned.
     *
     * @param headers The Kafka headers to retrieve the value from.
     * @param key The header key.
     * @param type The expected value type.
     * @param <T> The expected type of the header value.
     * @return The value or an empty Optional if the headers do not contain a correctly encoded value of the expected
     *         type for the given key.
     * @throws NullPointerException if key or type is {@code null}.
     * @see #createKafkaHeader(String, Object)
     */
    public static <T> Optional<T> getHeaderValue(final List<KafkaHeader> headers, final String key,
            final Class<T> type) {

        Objects.requireNonNull(key);
        Objects.requireNonNull(type);

        if (headers == null) {
            return Optional.empty();
        }

        return headers.stream()
                .filter(h -> key.equals(h.key()))
                .findFirst()
                .map(h -> decode(h, type));
    }

    /**
     * Returns the decoded value of the given Kafka header.
     *
     * @param header The header with the value to be decoded.
     * @param type The expected value type.
     * @param <T> The expected type of the header value.
     * @return The decoded value or {@code  null} if the header does not contain a correctly encoded value of the
     *         expected type for the given name.
     * @throws NullPointerException if type is {@code null}.
     * @see #createKafkaHeader(String, Object)
     */
    public static <T> T decode(final KafkaHeader header, final Class<T> type) {
        Objects.requireNonNull(type);

        if (header == null) {
            return null;
        }

        return decode(header.value(), type);
    }

    /**
     * Returns the decoded value of the given buffer.
     *
     * @param encodedHeaderValue The buffer with the value to be decoded.
     * @param type The expected value type.
     * @param <T> The expected type of the header value.
     * @return The decoded value or {@code  null} if the buffer does not contain a correctly encoded value of the
     *         expected type for the given name.
     * @throws NullPointerException if type is {@code null}.
     * @see #createKafkaHeader(String, Object)
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(final Buffer encodedHeaderValue, final Class<T> type) {
        Objects.requireNonNull(type);

        if (encodedHeaderValue == null) {
            return null;
        }

        try {
            if (String.class.equals(type)) {
                return (T) encodedHeaderValue.toString();
            } else {
                return Json.decodeValue(encodedHeaderValue, type);
            }
        } catch (DecodeException ex) {
            return null;
        }
    }
}
