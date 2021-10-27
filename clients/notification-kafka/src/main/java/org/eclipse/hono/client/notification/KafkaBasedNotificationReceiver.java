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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A client for receiving notifications with Kafka.
 */
public abstract class KafkaBasedNotificationReceiver implements NotificationReceiver {

    private final Vertx vertx;
    private final Map<String, String> consumerConfig = new HashMap<>();
    @SuppressWarnings("rawtypes")
    private final Map<Class<? extends Notification>, Handler> handlerPerType = new HashMap<>();
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
    public <T extends Notification> void registerConsumer(final Class<T> notificationType, final Handler<T> consumer) {

        if (started) {
            throw new IllegalStateException("consumers cannot be added when consumer is already started.");
        }

        final String address = getAddressForType(notificationType);
        topics.add(new HonoTopic(HonoTopic.Type.NOTIFICATION, address).toString());
        handlerPerType.put(notificationType, consumer);
    }

    private Handler<KafkaConsumerRecord<String, Buffer>> getRecordHandler() {
        return record -> handleNotification(decodeNotification(record.value()));
    }

    /**
     * Returns the address to consume notifications of the given type from.
     *
     * @param notificationType The class of the notifications to consume.
     * @param <T> The type of notifications to consume.
     * @return The address to consume notifications from as returned by {@link Notification#getAddress()}.
     */
    protected abstract <T extends Notification> String getAddressForType(Class<T> notificationType);

    /**
     * Decodes the received notification from JSON.
     * <p>
     * Subclasses can leverage {@link TypeIdResolver} or {@link JsonSubTypes} to decode abstract notifications.
     *
     * @param json A buffer containing the notification serialized as a JSON object.
     * @return A notification object.
     */
    protected abstract Notification decodeNotification(Buffer json);

    // visible for testing
    @SuppressWarnings("unchecked")
    void handleNotification(final Notification abstractNotification) {
        for (final var entry : handlerPerType.entrySet()) {
            final Class<? extends Notification> type = entry.getKey();

            if (type.isInstance(abstractNotification)) {
                entry.getValue().handle(abstractNotification);
                break;
            }
        }
    }

    @Override
    public Future<Void> stop() {
        return stopKafkaConsumer()
                .onComplete(v -> topics.clear())
                .onComplete(v -> handlerPerType.clear())
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

}
