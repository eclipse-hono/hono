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

package org.eclipse.hono.application.client.kafka.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageProperties;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.application.client.kafka.KafkaMessageProperties;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * A downstream message of Hono's Kafka-based north bound APIs.
 */
public class KafkaDownstreamMessage implements DownstreamMessage<KafkaMessageContext> {

    private final String tenantId;
    private final String deviceId;
    private final MessageProperties properties;
    private final String contentType;
    private final KafkaMessageContext messageContext;
    private final QoS qos;
    private final Buffer payload;
    private final Instant creationTime;
    private final Duration timeToLive;
    private final Integer timeTillDisconnect;

    /**
     * Creates a downstream message from the given Kafka consumer record.
     *
     * @param record The record.
     * @throws NullPointerException if the record is {@code null}.
     * @throws IllegalArgumentException if the topic does not contain a tenant id.
     */
    public KafkaDownstreamMessage(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        tenantId = getTenantIdFromTopic(record);
        deviceId = record.key();
        properties = new KafkaMessageProperties(record);
        contentType = getContentTypeHeaderValue(record.headers());
        messageContext = new KafkaMessageContext(record);
        qos = getQosHeaderValue(record.headers());
        payload = record.value();
        creationTime = getCreationTimeHeaderValue(record.headers());
        timeToLive = getTimeToLiveHeaderValue(record.headers());
        timeTillDisconnect = getTimeTillDisconnectHeaderValue(record.headers());
    }

    private String getTenantIdFromTopic(final KafkaConsumerRecord<String, Buffer> record) {
        return Optional.ofNullable(HonoTopic.fromString(record.topic()))
                .map(HonoTopic::getTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid topic name"));
    }

    private String getContentTypeHeaderValue(final List<KafkaHeader> headers) {
        return KafkaRecordHelper.getContentType(headers)
                .orElse(MessageHelper.CONTENT_TYPE_OCTET_STREAM);
    }

    private QoS getQosHeaderValue(final List<KafkaHeader> headers) {
        return KafkaRecordHelper.getQoS(headers)
                .orElse(QoS.AT_LEAST_ONCE);
    }

    private Instant getCreationTimeHeaderValue(final List<KafkaHeader> headers) {
        return KafkaRecordHelper.getCreationTime(headers)
                .orElse(null);
    }

    private Duration getTimeToLiveHeaderValue(final List<KafkaHeader> headers) {
        return KafkaRecordHelper.getHeaderValue(headers, MessageHelper.SYS_HEADER_PROPERTY_TTL, Long.class)
                .map(Duration::ofMillis)
                .orElse(null);
    }

    private Integer getTimeTillDisconnectHeaderValue(final List<KafkaHeader> headers) {
        return KafkaRecordHelper.getHeaderValue(headers, CommandConstants.MSG_PROPERTY_DEVICE_TTD, Integer.class)
                .orElse(null);
    }

    @Override
    public final String getTenantId() {
        return tenantId;
    }

    @Override
    public final String getDeviceId() {
        return deviceId;
    }

    @Override
    public final MessageProperties getProperties() {
        return properties;
    }

    @Override
    public final String getContentType() {
        return contentType;
    }

    @Override
    public final KafkaMessageContext getMessageContext() {
        return messageContext;
    }

    @Override
    public final QoS getQos() {
        return qos;
    }

    @Override
    public final Buffer getPayload() {
        return payload;
    }

    @Override
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getTimeToLive() {
        return timeToLive;
    }

    @Override
    public Integer getTimeTillDisconnect() {
        return timeTillDisconnect;
    }

    @Override
    public String getCorrelationId() {
        return properties.getProperty(MessageHelper.SYS_PROPERTY_CORRELATION_ID, String.class);
    }

    @Override
    public Integer getStatus() {
        return properties.getProperty(MessageHelper.APP_PROPERTY_STATUS, Integer.class);
    }
}
