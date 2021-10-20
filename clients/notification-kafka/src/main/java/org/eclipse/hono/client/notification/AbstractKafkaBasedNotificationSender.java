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

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.util.MessagingType;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;

/**
 * A client for publishing Hono internal notifications with Kafka.
 *
 * @param <T> The implementation type of notification to be sent.
 */
public abstract class AbstractKafkaBasedNotificationSender<T extends Notification> implements NotificationSender<T> {

    private static final String PRODUCER_NAME = "notification";

    private final KafkaProducerConfigProperties config;
    private final KafkaProducerFactory<String, JsonObject> producerFactory;

    private boolean stopped = false;

    /**
     * Creates a new Kafka-based event sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public AbstractKafkaBasedNotificationSender(
            final KafkaProducerFactory<String, JsonObject> producerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig) {
        Objects.requireNonNull(producerFactory);
        Objects.requireNonNull(kafkaProducerConfig);

        this.producerFactory = producerFactory;
        this.config = kafkaProducerConfig;
    }

    @Override
    public Future<Void> publish(final T notification, final SpanContext context) {

        Objects.requireNonNull(notification);

        if (stopped) {
            return Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "sender already stopped"));
        }

        try {

            final String topic = getTopic(notification);
            final String key = getKey(notification);
            final JsonObject value = JsonObject.mapFrom(notification);
            final KafkaProducerRecord<String, JsonObject> record = KafkaProducerRecord.create(topic, key, value);

            final Promise<RecordMetadata> sendPromise = Promise.promise();
            producerFactory.getOrCreateProducer(PRODUCER_NAME, config).send(record, sendPromise);

            return sendPromise.future()
                    .recover(t -> Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t)))
                    .mapEmpty();
        } catch (RuntimeException ex) {
            return Future.failedFuture(ex);
        }
    }

    /**
     * Gets the record key to be used for a given notification.
     *
     * @param notification The notification to determine the key for.
     * @return The record key.
     */
    protected abstract String getKey(T notification);

    /**
     * Gets the topic to be used for a given notification.
     *
     * @param notification The notification to determine the topic for.
     * @return The topic.
     */
    protected abstract String getTopic(T notification);

    /**
     * {@inheritDoc}
     * <p>
     * Starts the producer.
     */
    @Override
    public Future<Void> start() {
        stopped = false;
        producerFactory.getOrCreateProducer(PRODUCER_NAME, config);
        return Future.succeededFuture();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Closes the producer.
     */
    @Override
    public Future<Void> stop() {
        stopped = true;
        return producerFactory.closeProducer(PRODUCER_NAME);
    }

    @Override
    public String toString() {
        return AbstractKafkaBasedNotificationSender.class.getName() + " via Kafka";
    }

    @Override
    public final MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

}
