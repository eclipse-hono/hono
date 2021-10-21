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

package org.eclipse.hono.client.notification;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.util.Strings;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A client for receiving notifications with Kafka.
 */
public class KafkaBasedNotificationReceiver implements NotificationReceiver {

    private final Vertx vertx;
    private final Map<String, String> consumerConfig = new HashMap<>();
    private final Map<String, NotificationConsumer> consumers = new HashMap<>();
    private final Set<String> topics = new HashSet<>();

    private Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier;
    private HonoKafkaConsumer honoKafkaConsumer;
    private boolean started = false;

    /**
     * Creates a client for consuming notifications.
     *
     * @param vertx The vert.x instance to be used.
     * @param consumerConfig The configuration for the Kafka consumer.
     *
     * @throws NullPointerException if any parameter is {@code null}.
     * @throws IllegalArgumentException if consumerConfig does not contain a valid configuration (at least a bootstrap
     *             server).
     */
    public KafkaBasedNotificationReceiver(final Vertx vertx, final Map<String, String> consumerConfig) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(consumerConfig);

        if (Strings.isNullOrEmpty(consumerConfig.get(AbstractKafkaConfigProperties.PROPERTY_BOOTSTRAP_SERVERS))) {
            throw new IllegalArgumentException("No Kafka configuration found!");
        }

        this.vertx = vertx;
        this.consumerConfig.putAll(consumerConfig);
    }

    // visible for testing
    void setKafkaConsumerFactory(final Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier) {
        this.kafkaConsumerSupplier = Objects.requireNonNull(kafkaConsumerSupplier);
    }

    @Override
    public Future<Void> start() {
        honoKafkaConsumer = new HonoKafkaConsumer(vertx, topics, getRecordHandler(), consumerConfig);
        Optional.ofNullable(kafkaConsumerSupplier).ifPresent(honoKafkaConsumer::setKafkaConsumerSupplier);
        return honoKafkaConsumer
                .start()
                .onSuccess(ok -> started = true);
    }

    @Override
    public void addConsumer(final NotificationConsumer consumer) {

        if (started) {
            throw new IllegalStateException("consumers cannot be added when consumer is already started.");
        }

        consumers.put(consumer.getType(), consumer);
        topics.add(new HonoTopic(HonoTopic.Type.NOTIFICATION, consumer.getAddress()).toString());
    }

    private Handler<KafkaConsumerRecord<String, Buffer>> getRecordHandler() {
        return record -> {
            try {
                final Buffer value = record.value();
                final JsonObject jsonObject = value.toJsonObject();
                final String type = jsonObject.getString(NotificationConstants.JSON_FIELD_TYPE);

                consumers.get(type).handle(value);
            } catch (DecodeException | NullPointerException ex) {
                // TODO
            }
        };
    }

    @Override
    public Future<Void> stop() {
        return stopKafkaConsumer()
                .compose(v -> closeConsumers())
                .onComplete(v -> topics.clear())
                .onComplete(v -> consumers.clear())
                .onComplete(v -> started = false)
                .mapEmpty();
    }

    private Future<Void> stopKafkaConsumer() {
        if (honoKafkaConsumer != null) {
            return honoKafkaConsumer.stop();
        } else {
            return Future.succeededFuture();
        }
    }

    @SuppressWarnings("rawtypes")
    private CompositeFuture closeConsumers() {
        final List<Future> consumerCloseTracker = consumers.values().stream()
                .map(NotificationConsumer::close)
                .collect(Collectors.toList());
        return CompositeFuture.join(consumerCloseTracker);
    }
}
