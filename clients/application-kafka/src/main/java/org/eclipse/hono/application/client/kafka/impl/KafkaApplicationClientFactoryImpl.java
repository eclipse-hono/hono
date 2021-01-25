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

import java.util.Objects;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.kafka.KafkaApplicationClientFactory;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * A factory for creating clients for Hono's Kafka-based northbound APIs.
 */
public class KafkaApplicationClientFactoryImpl implements KafkaApplicationClientFactory {

    private final Vertx vertx;
    private final KafkaConsumerConfigProperties config;

    /**
     * Creates a new factory.
     *
     * @param vertx The Vert.x instance to use.
     * @param config The Kafka consumer configuration properties to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if config does not contain Kafka configuration properties.
     */
    public KafkaApplicationClientFactoryImpl(final Vertx vertx, final KafkaConsumerConfigProperties config) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(config);

        if (!config.isConfigured()) {
            throw new IllegalArgumentException("No Kafka configuration found!");
        }

        this.vertx = vertx;
        this.config = config;
    }

    @Override
    public Future<MessageConsumer> createTelemetryConsumer(final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);

        final KafkaConsumer<String, Buffer> kafkaConsumer = KafkaConsumer.create(vertx, config.getConsumerConfig());
        final Handler<Throwable> effectiveCloseHandler = closeHandler != null ? closeHandler : (t -> {});

        return TelemetryConsumer.create(kafkaConsumer, config, tenantId, messageHandler, effectiveCloseHandler);
    }

    @Override
    public Future<MessageConsumer> createEventConsumer(final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);

        final KafkaConsumer<String, Buffer> kafkaConsumer = KafkaConsumer.create(vertx, config.getConsumerConfig());
        final Handler<Throwable> effectiveCloseHandler = closeHandler != null ? closeHandler : (t -> {});

        return EventConsumer.create(kafkaConsumer, config, tenantId, messageHandler, effectiveCloseHandler);
    }

    // TODO add support for command & control
}
