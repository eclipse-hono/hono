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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.Consumer;
import org.eclipse.hono.client.kafka.AbstractKafkaConfigProperties;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.util.Strings;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A client for receiving notifications with Kafka.
 *
 * @param <T> The implementation type of notification to consume.
 */
public class KafkaBasedNotificationReceiver<T extends Notification> implements NotificationReceiver {

    private final Vertx vertx;
    private final Map<String, String> consumerConfig = new HashMap<>();

    private final NotificationAddressProvider<T> addressProvider;
    private final Handler<T> notificationHandler;
    private final Class<T> notificationClass;
    private Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier;
    private HonoKafkaConsumer honoKafkaConsumer;

    /**
     * Creates a client for consuming notifications.
     *
     * @param vertx The vert.x instance to be used.
     * @param consumerConfig The configuration for the Kafka consumer.
     * @param addressProvider The address provider to get the address to be used in the Kafka topic for the notification
     *            type.
     * @param notificationHandler The handler to invoke with every notification received. The message passed in will be
     *            acknowledged automatically if the handler does not throw an exception.
     * @param notificationClass The class to decode the notification into.
     * @throws NullPointerException if any parameter is {@code null}.
     * @throws IllegalArgumentException if consumerConfig does not contain a valid configuration (at least a bootstrap
     *             server).
     */
    public KafkaBasedNotificationReceiver(final Vertx vertx, final Map<String, String> consumerConfig,
            final NotificationAddressProvider<T> addressProvider,
            final Handler<T> notificationHandler, final Class<T> notificationClass) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(consumerConfig);
        Objects.requireNonNull(addressProvider);
        Objects.requireNonNull(notificationHandler);
        Objects.requireNonNull(notificationClass);

        if (Strings.isNullOrEmpty(consumerConfig.get(AbstractKafkaConfigProperties.PROPERTY_BOOTSTRAP_SERVERS))) {
            throw new IllegalArgumentException("No Kafka configuration found!");
        }

        this.vertx = vertx;
        this.addressProvider = addressProvider;
        this.consumerConfig.putAll(consumerConfig);
        this.notificationHandler = notificationHandler;
        this.notificationClass = notificationClass;
    }

    // visible for testing
    void setKafkaConsumerFactory(final Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier) {
        this.kafkaConsumerSupplier = Objects.requireNonNull(kafkaConsumerSupplier);
    }

    @Override
    public Future<Void> start() {
        final String address = addressProvider.apply(notificationClass);
        final String topic = new HonoTopic(HonoTopic.Type.NOTIFICATION, address).toString();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            try {
                final T notification = Json.decodeValue(record.value(), notificationClass);
                notificationHandler.handle(notification);
            } catch (DecodeException decodeException) {
                // TODO
            }
        };

        honoKafkaConsumer = new HonoKafkaConsumer(vertx, Set.of(topic), recordHandler, consumerConfig);
        Optional.ofNullable(kafkaConsumerSupplier).ifPresent(honoKafkaConsumer::setKafkaConsumerSupplier);
        return honoKafkaConsumer.start()
                .mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        if (honoKafkaConsumer != null) {
            return honoKafkaConsumer.stop();
        } else {
            return Future.succeededFuture();
        }
    }
}
