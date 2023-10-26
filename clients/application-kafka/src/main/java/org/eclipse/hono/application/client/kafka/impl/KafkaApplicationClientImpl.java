/*
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.kafka.KafkaApplicationClient;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A Kafka based client that supports Hono's north bound operations to send commands and receive telemetry,
 * event and command response messages.
 */
public class KafkaApplicationClientImpl extends KafkaBasedCommandSender implements KafkaApplicationClient {

    private final Vertx vertx;
    private final MessagingKafkaConsumerConfigProperties consumerConfig;
    private final List<MessageConsumer> consumersToCloseOnStop = new ArrayList<>();
    private Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier;

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
            final MessagingKafkaConsumerConfigProperties consumerConfig,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig) {
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
            final MessagingKafkaConsumerConfigProperties consumerConfig,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(vertx, consumerConfig, producerFactory, producerConfig, tracer);

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(consumerConfig);
        if (!consumerConfig.isConfigured() || !producerConfig.isConfigured()) {
            throw new IllegalArgumentException("No Kafka configuration found!");
        }
        this.vertx = vertx;
        this.consumerConfig = consumerConfig;
    }

    @Override
    public void addOnClientReadyHandler(final Handler<AsyncResult<Void>> handler) {
        addOnKafkaProducerReadyHandler(handler);
    }

    @Override
    public Future<Void> stop() {
        // stop created consumers
        final List<Future<Void>> closeKafkaClientsTracker = consumersToCloseOnStop.stream()
                .map(MessageConsumer::close)
                .collect(Collectors.toList());
        // add command sender related clients
        closeKafkaClientsTracker.add(super.stop());
        return Future.join(closeKafkaClientsTracker)
                .mapEmpty();
    }

    @Override
    public Future<MessageConsumer> createTelemetryConsumer(final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createKafkaBasedDownstreamMessageConsumer(tenantId, HonoTopic.Type.TELEMETRY, messageHandler);
    }

    @Override
    public Future<MessageConsumer> createEventConsumer(final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createKafkaBasedDownstreamMessageConsumer(tenantId, HonoTopic.Type.EVENT, messageHandler);
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

        return createKafkaBasedDownstreamMessageConsumer(tenantId, HonoTopic.Type.COMMAND_RESPONSE, messageHandler);
    }

    // visible for testing
    void setKafkaConsumerFactory(final Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier) {
        this.kafkaConsumerSupplier = Objects.requireNonNull(kafkaConsumerSupplier);
    }


    private Future<MessageConsumer> createKafkaBasedDownstreamMessageConsumer(
            final String tenantId,
            final HonoTopic.Type type,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(messageHandler);

        final String topic = new HonoTopic(type, tenantId).toString();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            messageHandler.handle(new KafkaDownstreamMessage(record));
        };
        final Promise<Void> readyTracker = Promise.promise();
        final HonoKafkaConsumer<Buffer> consumer = new HonoKafkaConsumer<>(vertx, Set.of(topic), recordHandler,
                consumerConfig.getConsumerConfig(type.toString()));
        consumer.setPollTimeout(Duration.ofMillis(consumerConfig.getPollTimeout()));
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        Optional.ofNullable(kafkaConsumerSupplier)
                .ifPresent(consumer::setKafkaConsumerSupplier);
        return consumer.start()
                .compose(ok -> readyTracker.future())
                .map(v -> (MessageConsumer) new MessageConsumer() {
                    @Override
                    public Future<Void> close() {
                        return consumer.stop();
                    }
                })
                .onSuccess(consumersToCloseOnStop::add);
    }
}
