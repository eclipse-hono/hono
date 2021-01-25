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

package org.eclipse.hono.application.client.kafka.impl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageProperties;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.application.client.kafka.KafkaMessageProperties;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.kafka.client.KafkaMessageHelper;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * A downstream message of Hono's Kafka-based northbound APIs.
 */
public class KafkaDownstreamMessage implements DownstreamMessage<KafkaMessageContext> {

    private final Logger log = LoggerFactory.getLogger(KafkaDownstreamMessage.class);

    private final String tenantId;
    private final String deviceId;
    private final MessageProperties properties;
    private final String contentType;
    private final KafkaMessageContext messageContext;
    private final QoS qos;
    private final Buffer payload;

    /**
     * Creates a downstream message from the given Kafka consumer record.
     *
     * @param record The record.
     * @throws NullPointerException if the record is {@code null}.
     */
    public KafkaDownstreamMessage(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        tenantId = getTenantId(record);
        deviceId = record.key();
        properties = new KafkaMessageProperties(record);
        contentType = getContentType(record.headers());
        messageContext = new KafkaMessageContext(record);
        qos = getQoS(record.headers());
        payload = record.value();

    }

    private String getTenantId(final KafkaConsumerRecord<String, Buffer> record) {
        return Optional.ofNullable(HonoTopic.fromString(record.topic()))
                .map(HonoTopic::getTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid topic name"));
    }

    private String getContentType(final List<KafkaHeader> headers) {
        return KafkaMessageHelper.getContentType(headers)
                .orElseGet(() -> {
                    log.debug("content type not present in Kafka record");
                    return MessageHelper.CONTENT_TYPE_OCTET_STREAM;
                });
    }

    private QoS getQoS(final List<KafkaHeader> headers) {
        return KafkaMessageHelper.getQoS(headers)
                .orElseGet(() -> {
                    log.debug("QoS not present in Kafka record");
                    return QoS.AT_LEAST_ONCE;
                });
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
}
