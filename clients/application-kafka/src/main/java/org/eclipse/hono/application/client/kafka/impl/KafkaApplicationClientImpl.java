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
import org.eclipse.hono.application.client.kafka.KafkaApplicationClient;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * A Kafka based client that supports Hono's north bound operations to send commands and receive telemetry,
 * event and command response messages.
 */
public class KafkaApplicationClientImpl extends KafkaBasedCommandSender implements KafkaApplicationClient {

    private final Vertx vertx;
    private final KafkaConsumerConfigProperties consumerConfig;

    /**
     * Creates a new Kafka based application client.
     *
     * @param vertx The Vert.x instance to use.
     * @param consumerConfig The Kafka consumer configuration properties to use.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration properties to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if consumerConfig or producerConfig does not contain
     *                                  Kafka configuration properties.
     */
    public KafkaApplicationClientImpl(
            final Vertx vertx,
            final KafkaConsumerConfigProperties consumerConfig,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final KafkaProducerConfigProperties producerConfig) {
        this(vertx, consumerConfig, producerFactory, producerConfig, NoopTracerFactory.create());
    }

    /**
     * Creates a new Kafka based application client.
     *
     * @param vertx The Vert.x instance to use.
     * @param consumerConfig The Kafka consumer configuration properties to use.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @throws IllegalArgumentException if consumerConfig or producerConfig does not contain
     *                                  Kafka configuration properties.
     */
    public KafkaApplicationClientImpl(
            final Vertx vertx,
            final KafkaConsumerConfigProperties consumerConfig,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final KafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, producerConfig, tracer);

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(consumerConfig);
        if (!consumerConfig.isConfigured() || !producerConfig.isConfigured()) {
            throw new IllegalArgumentException("No Kafka configuration found!");
        }
        this.vertx = vertx;
        this.consumerConfig = consumerConfig;
    }

    @Override
    public Future<MessageConsumer> createTelemetryConsumer(final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createKafkaBasedDownstreamMessageConsumer(tenantId, HonoTopic.Type.TELEMETRY, messageHandler,
                closeHandler);
    }

    @Override
    public Future<MessageConsumer> createEventConsumer(final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createKafkaBasedDownstreamMessageConsumer(tenantId, HonoTopic.Type.EVENT, messageHandler,
                closeHandler);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The replyId is not used in the Kafka based implementation. It can be set to {@code null}. 
     * If set it will be ignored.
     *
     * @throws NullPointerException if tenantId, or messageHandler is {@code null}.
     */
    @Override
    public Future<MessageConsumer> createCommandResponseConsumer(final String tenantId, final String replyId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createKafkaBasedDownstreamMessageConsumer(tenantId, HonoTopic.Type.COMMAND_RESPONSE, messageHandler,
                closeHandler);
    }

    private Future<MessageConsumer> createKafkaBasedDownstreamMessageConsumer(
            final String tenantId,
            final HonoTopic.Type type,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(messageHandler);

        final KafkaConsumer<String, Buffer> kafkaConsumer = KafkaConsumer.create(vertx,
                consumerConfig.getConsumerConfig(type.toString()));
        final Handler<Throwable> effectiveCloseHandler = Objects.nonNull(closeHandler) ? closeHandler : (t -> {});

        return KafkaBasedDownstreamMessageConsumer.create(tenantId, type, kafkaConsumer, consumerConfig, messageHandler,
                effectiveCloseHandler);
    }
}
