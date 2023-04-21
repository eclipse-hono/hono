/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.command.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * A command used in a Kafka based client.
 *
 */
public final class KafkaBasedCommand implements Command {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommand.class);

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
    private final boolean responseRequired;

    private String gatewayId;

    private KafkaBasedCommand(
            final Optional<String> validationError,
            final KafkaConsumerRecord<String, Buffer> commandRecord,
            final String tenantId,
            final String deviceId,
            final String correlationId,
            final String subject,
            final String contentType,
            final boolean responseRequired) {

        this.validationError = validationError;
        this.record = commandRecord;
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.correlationId = correlationId;
        this.subject = subject;
        this.contentType = contentType;
        this.responseRequired = responseRequired;
        requestId = Commands.encodeRequestIdParameters(correlationId, MessagingType.kafka);
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
     * <li>a non-empty <em>correlation-id</em> header if the <em>response-required</em> header is
     * set to {@code true}.</li>
     * </ul>
     * or otherwise the returned command's {@link #isValid()} method will return {@code false}.
     *
     * @param record The record containing the command.
     * @return The command.
     * @throws NullPointerException if record is {@code null}.
     * @throws IllegalArgumentException if the record's topic is not a proper command topic or if the record's
     *                                  headers do not contain a target device identifier matching the record's key.
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
        return from(record, tenantId);
    }

    /**
     * Creates a command from a Kafka consumer record, forwarded by the Command Router.
     * <p>
     * The message is required to contain a <em>tenant_id</em> header and a <em>key</em> with the command
     * target device id, matching the value of the <em>device_id</em> header.
     * If that is not the case, an {@link IllegalArgumentException} is thrown.
     * <p>
     * In addition, the record is expected to contain
     * <ul>
     * <li>a <em>subject</em> header</li>
     * <li>a non-empty <em>correlation-id</em> header if the <em>response-expected</em> header is
     * set to {@code true}.</li>
     * </ul>
     * or otherwise the returned command's {@link #isValid()} method will return {@code false}.
     * <p>
     * If the <em>via</em> header is set, its value will be set as target gateway of the created command.
     *
     * @param record The record containing the command.
     * @return The command.
     * @throws NullPointerException if record is {@code null}.
     * @throws IllegalArgumentException if the record's headers do not contain a tenant identifier and a target
     *                                  device identifier matching the record's key.
     */
    public static KafkaBasedCommand fromRoutedCommandRecord(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        final String tenantId = KafkaRecordHelper.getTenantId(record.headers())
                .filter(id -> !id.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("tenant is not set"));
        final KafkaBasedCommand command = from(record, tenantId);

        KafkaRecordHelper.getViaHeader(record.headers())
                .filter(id -> !id.isEmpty())
                .ifPresent(command::setGatewayId);

        return command;
    }

    private static KafkaBasedCommand from(
            final KafkaConsumerRecord<String, Buffer> record,
            final String tenantId) {

        final String deviceId = KafkaRecordHelper.getDeviceId(record.headers())
                .filter(id -> !id.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("device identifier is not set"));
        if (!deviceId.equals(record.key())) {
            throw new IllegalArgumentException("device identifier not set as record key");
        }

        final StringJoiner validationErrorJoiner = new StringJoiner(", ");
        final String subject = KafkaRecordHelper.getSubject(record.headers())
                .orElseGet(() -> {
                    validationErrorJoiner.add("subject not set");
                    return null;
                });
        final String contentType = KafkaRecordHelper.getContentType(record.headers()).orElse(null);
        final boolean responseRequired = KafkaRecordHelper.isResponseRequired(record.headers());
        final String correlationId = KafkaRecordHelper.getCorrelationId(record.headers())
                .filter(id -> !id.isEmpty())
                .orElseGet(() -> {
                    if (responseRequired) {
                        validationErrorJoiner.add("correlation-id is not set");
                    }
                    return null;
                });

        return new KafkaBasedCommand(
                validationErrorJoiner.length() > 0 ? Optional.of(validationErrorJoiner.toString()) : Optional.empty(),
                record,
                tenantId,
                deviceId,
                correlationId,
                subject,
                contentType,
                responseRequired);
    }

    @Override
    public Map<String, String> getDeliveryFailureNotificationProperties() {
        return record.headers().stream()
                .filter(header -> header.key()
                        .startsWith(KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX))
                .collect(Collectors.toMap(KafkaHeader::key, header -> header.value().toString(), (v1, v2) -> {
                    LOG.debug("ignoring duplicate delivery notification header with value [{}] for {}", v2, this);
                    return v1;
                }));
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

    @Override
    public boolean isOneWay() {
        return !responseRequired;
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

    /**
     * Returns the kafka consumer record corresponding to this command.
     *
     * @return The kafka consumer record.
     */
    public KafkaConsumerRecord<String, Buffer> getRecord() {
        return record;
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
                return "Command [name: %s, tenant-id: %s, gateway-id: %s, device-id: %s, request-id: %s, partition offset: %s]"
                        .formatted(getName(), tenantId, gatewayId, deviceId, requestId, record.offset());
            } else {
                return "Command [name: %s, tenant-id: %s, device-id: %s, request-id: %s, partition offset: %s]"
                        .formatted(getName(), tenantId, deviceId, requestId, record.offset());
            }
        } else {
            return "Invalid Command [tenant-id: %s, device-id: %s, partition offset: %s, error: %s]"
                    .formatted(tenantId, deviceId, record.offset(), validationError.get());
        }
    }

    @Override
    public void logToSpan(final Span span) {
        Objects.requireNonNull(span);
        if (isValid()) {
            TracingHelper.TAG_CORRELATION_ID.set(span, correlationId);
            final Map<String, Object> items = new HashMap<>(3);
            items.put(Fields.EVENT, "received command message via Kafka");
            items.put("subject", subject);
            items.put("content-type", contentType);
            span.log(items);
        } else {
            TracingHelper.logError(span, "received invalid command message [" + this + "]");
        }
    }

}
