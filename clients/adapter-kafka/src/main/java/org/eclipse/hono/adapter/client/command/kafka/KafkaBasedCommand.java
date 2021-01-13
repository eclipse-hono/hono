/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.client.command.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.Commands;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.util.RecordUtil;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Strings;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A command used in a Kafka based client.
 *
 */
public final class KafkaBasedCommand implements Command {

    /**
     * If present, the command is invalid.
     */
    private final Optional<String> validationError;
    private final KafkaConsumerRecord<String, Buffer> record;
    private final String tenantId;
    private final String deviceId;
    private final String originalDeviceId;
    private final String correlationId;
    private final String subject;
    private final String contentType;
    private final String requestId;

    private KafkaBasedCommand(
            final Optional<String> validationError,
            final KafkaConsumerRecord<String, Buffer> commandRecord,
            final String tenantId,
            final String deviceId,
            final String originalDeviceId,
            final String correlationId,
            final String subject,
            final String contentType) {

        this.validationError = validationError;
        this.record = commandRecord;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.originalDeviceId = originalDeviceId;
        this.correlationId = correlationId;
        this.subject = subject;
        this.contentType = contentType;
        this.requestId = Commands.getRequestId(correlationId);
    }

    /**
     * Creates a command from a Kafka consumer record.
     * <p>
     * The record is expected to contain
     * <ul>
     * <li>a <em>topic</em> of the format <em>hono.command.${tenant_id}</em></li>
     * <li>a <em>key</em> with the command target device id, matching the value of the <em>device_id</em> header</li>
     * <li>a <em>subject</em> header</li>
     * <li>a non-empty <em>correlation-id</em> header if a response is expected for the command.</li>
     * </ul>
     * <p>
     * If any of the requirements above are not met, then the returned command's {@link #isValid()}
     * method will return {@code false}.
     *
     * @param record The record containing the command.
     * @param deviceId The identifier of the device that the command will be sent to. If the command has been mapped
     *                 to a gateway, this id is the gateway id and the original command target device is given in
     *                 the <em>device_id</em> header and the key of the record.
     * @return The command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static KafkaBasedCommand from(final KafkaConsumerRecord<String, Buffer> record, final String deviceId) {

        Objects.requireNonNull(record);
        Objects.requireNonNull(deviceId);

        String tenantId = null;
        final StringJoiner validationErrorJoiner = new StringJoiner(", ");
        if (Strings.isNullOrEmpty(record.topic())) {
            validationErrorJoiner.add("topic is not set");
        } else {
            final HonoTopic honoTopic = HonoTopic.fromString(record.topic());
            if (honoTopic == null || !honoTopic.getType().equals(HonoTopic.Type.COMMAND)) {
                validationErrorJoiner.add("unsupported topic");
            } else {
                tenantId = honoTopic.getTenantId();
            }
        }
        final String originalDeviceId = getHeader(record, MessageHelper.APP_PROPERTY_DEVICE_ID);
        if (Strings.isNullOrEmpty(originalDeviceId)) {
            validationErrorJoiner.add("device identifier is not set");
        } else if (!originalDeviceId.equals(record.key())) {
            validationErrorJoiner.add("device identifier not set as record key");
        }
        final String subject = getHeader(record, MessageHelper.SYS_PROPERTY_SUBJECT);
        if (subject == null) {
            validationErrorJoiner.add("subject not set");
        }
        final String contentType = getHeader(record, MessageHelper.SYS_PROPERTY_CONTENT_TYPE);
        final String correlationId = getHeader(record, MessageHelper.SYS_PROPERTY_CORRELATION_ID);

        return new KafkaBasedCommand(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                record,
                tenantId,
                deviceId,
                originalDeviceId,
                Strings.isNullOrEmpty(correlationId) ? null : correlationId,
                subject,
                contentType);
    }

    private static String getHeader(final KafkaConsumerRecord<String, Buffer> record, final String header) {
        return RecordUtil.getStringHeaderValue(record.headers(), header);
    }

    @Override
    public boolean isOneWay() {
        return correlationId == null;
    }

    @Override
    public boolean isValid() {
        return !validationError.isPresent();
    }

    @Override
    public String getInvalidCommandReason() {
        if (!validationError.isPresent()) {
            throw new IllegalStateException("command is valid");
        }
        return validationError.get();
    }

    @Override
    public String getName() {
        requireValid();
        return subject;
    }

    @Override
    public String getTenant() {
        requireValid();
        return tenantId;
    }

    @Override
    public String getDeviceId() {
        requireValid();
        return deviceId;
    }

    @Override
    public boolean isTargetedAtGateway() {
        requireValid();
        return originalDeviceId != null && !originalDeviceId.equals(deviceId);
    }

    @Override
    public String getOriginalDeviceId() {
        requireValid();
        return originalDeviceId;
    }

    @Override
    public String getRequestId() {
        requireValid();
        return requestId;
    }

    @Override
    public Buffer getPayload() {
        requireValid();
        return record.value();
    }

    @Override
    public int getPayloadSize() {
        return Optional.ofNullable(getPayload()).map(Buffer::length).orElse(0);
    }

    @Override
    public String getContentType() {
        requireValid();
        return contentType;
    }

    @Override
    public String getReplyToId() {
        requireValid();
        // no reply-to set in the command message
        return null;
    }

    @Override
    public String getCorrelationId() {
        requireValid();
        return correlationId;
    }

    private void requireValid() {
        if (!isValid()) {
            throw new IllegalStateException("command is invalid");
        }
    }

    @Override
    public String toString() {
        if (isValid()) {
            if (isTargetedAtGateway()) {
                return String.format("Command [name: %s, tenant-id: %s, device-id %s, original device-id %s, request-id: %s]",
                        getName(), getTenant(), getDeviceId(), getOriginalDeviceId(), getRequestId());
            } else {
                return String.format("Command [name: %s, tenant-id: %s, device-id %s, request-id: %s]",
                        getName(), getTenant(), getDeviceId(), getRequestId());
            }
        } else {
            return String.format("Invalid Command [tenant-id: %s, device-id: %s. error: %s]", tenantId, deviceId, validationError.get());
        }
    }

    @Override
    public void logToSpan(final Span span) {
        Objects.requireNonNull(span);
        if (isValid()) {
            TracingHelper.TAG_CORRELATION_ID.set(span, correlationId);
            final Map<String, Object> items = new HashMap<>(3);
            items.put(Fields.EVENT, "received command message");
            items.put("subject", subject);
            items.put("content-type", contentType);
            span.log(items);
        } else {
            TracingHelper.logError(span, "received invalid command message [" + this + "]");
        }
    }

}
