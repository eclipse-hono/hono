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
     * The name of the boolean Kafka record header that defines whether a response is expected for the command.
     */
    public static final String HEADER_RESPONSE_EXPECTED = "response-expected";

    /**
     * If present, the command is invalid.
     */
    private final Optional<String> validationError;
    private final KafkaConsumerRecord<String, Buffer> record;
    private final String tenantId;
    private final String deviceId;
    private final String correlationId;
    private final String subject;
    private final String contentType;
    private final String requestId;
    private final boolean responseExpected;

    private String gatewayId;

    private KafkaBasedCommand(
            final Optional<String> validationError,
            final KafkaConsumerRecord<String, Buffer> commandRecord,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String subject,
            final String contentType,
            final boolean responseExpected) {

        this.validationError = validationError;
        this.record = commandRecord;
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.correlationId = correlationId;
        this.subject = subject;
        this.contentType = contentType;
        this.responseExpected = responseExpected;
        requestId = Commands.getRequestId(correlationId);
    }

    /**
     * Creates a command from a Kafka consumer record.
     * <p>
     * The message is required to contain a <em>topic</em> of the format <em>hono.command.${tenant_id}</em>
     * and a <em>key</em> with the command target device id, matching the value of the <em>device_id</em> header.
     * If that is not the case, an {@link IllegalArgumentException} is thrown.
     * <p>
     * In addition, the record is expected to contain
     * <ul>
     * <li>a <em>subject</em> header</li>
     * <li>a non-empty <em>correlation-id</em> header if the <em>response-expected</em> header is set to {@code true}.</li>
     * </ul>
     * or otherwise the returned command's {@link #isValid()} method will return {@code false}.
     * <p>
     *
     * @param record The record containing the command.
     * @return The command.
     * @throws NullPointerException if record is {@code null}.
     * @throws IllegalArgumentException if the record doesn't correctly reference a target device.
     */
    public static KafkaBasedCommand from(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        if (Strings.isNullOrEmpty(record.topic())) {
            throw new IllegalArgumentException("topic is not set");
        }
        final HonoTopic honoTopic = HonoTopic.fromString(record.topic());
        if (honoTopic == null || !honoTopic.getType().equals(HonoTopic.Type.COMMAND)) {
            throw new IllegalArgumentException("unsupported topic");
        }
        final String tenantId = honoTopic.getTenantId();
        final String deviceId = getHeader(record, MessageHelper.APP_PROPERTY_DEVICE_ID);
        if (Strings.isNullOrEmpty(deviceId)) {
            throw new IllegalArgumentException("device identifier is not set");
        } else if (!deviceId.equals(record.key())) {
            throw new IllegalArgumentException("device identifier not set as record key");
        }

        final StringJoiner validationErrorJoiner = new StringJoiner(", ");
        final String subject = getHeader(record, MessageHelper.SYS_PROPERTY_SUBJECT);
        if (subject == null) {
            validationErrorJoiner.add("subject not set");
        }
        final String contentType = getHeader(record, MessageHelper.SYS_PROPERTY_CONTENT_TYPE);
        final String correlationId = getHeader(record, MessageHelper.SYS_PROPERTY_CORRELATION_ID);
        final boolean responseExpected = Optional
                .ofNullable(RecordUtil.getHeaderValue(record.headers(), HEADER_RESPONSE_EXPECTED, Boolean.class))
                .orElse(false);
        if (responseExpected && Strings.isNullOrEmpty(correlationId)) {
            validationErrorJoiner.add("correlation-id is not set");
        }

        return new KafkaBasedCommand(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                record,
                tenantId,
                deviceId,
                Strings.isNullOrEmpty(correlationId) ? null : correlationId,
                subject,
                contentType,
                responseExpected);
    }

    /**
     * Creates a command from a Kafka consumer record.
     * <p>
     * The command is created as described in {@link #from(KafkaConsumerRecord)}.
     * Additionally, gateway information is extracted from the <em>via</em> header of the record.
     *
     * @param record The record containing the command.
     * @return The command.
     * @throws NullPointerException if record is {@code null}.
     */
    public static KafkaBasedCommand fromRoutedCommandRecord(final KafkaConsumerRecord<String, Buffer> record) {
        final KafkaBasedCommand command = from(record);
        final String gatewayId = getHeader(record, MessageHelper.APP_PROPERTY_CMD_VIA);
        if (!Strings.isNullOrEmpty(gatewayId)) {
            command.setGatewayId(gatewayId);
        }
        return command;
    }

    private static String getHeader(final KafkaConsumerRecord<String, Buffer> record, final String header) {
        return RecordUtil.getStringHeaderValue(record.headers(), header);
    }

    @Override
    public boolean isOneWay() {
        return !responseExpected;
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
    public String getTenant() {
        return tenantId;
    }

    @Override
    public String getGatewayOrDeviceId() {
        return Optional.ofNullable(gatewayId).orElse(deviceId);
    }

    @Override
    public boolean isTargetedAtGateway() {
        return gatewayId != null;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * A scenario where the gateway information isn't taken from the command record
     * (see {@link #fromRoutedCommandRecord(KafkaConsumerRecord)}) but instead needs
     * to be set manually here would be the case of a gateway subscribing for commands
     * targeted at a specific device. In that scenario, the Command Router does the
     * routing based on the edge device identifier and only the target protocol adapter
     * knows about the gateway association.
     *
     * @param gatewayId The gateway identifier.
     */
    @Override
    public void setGatewayId(final String gatewayId) {
        this.gatewayId = gatewayId;
    }

    @Override
    public String getName() {
        requireValid();
        return subject;
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
        return Optional.ofNullable(record.value()).map(Buffer::length).orElse(0);
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
                return String.format("Command [name: %s, tenant-id: %s, gateway-id: %s, device-id: %s, request-id: %s]",
                        getName(), tenantId, gatewayId, deviceId, requestId);
            } else {
                return String.format("Command [name: %s, tenant-id: %s, device-id: %s, request-id: %s]",
                        getName(), tenantId, deviceId, requestId);
            }
        } else {
            return String.format("Invalid Command [tenant-id: %s, device-id: %s. error: %s]", tenantId,
                    deviceId, validationError.get());
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
