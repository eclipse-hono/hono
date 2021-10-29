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
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;

/**
 * A client for publishing notifications with Kafka.
 */
public class KafkaBasedNotificationSender implements NotificationSender {

    public static final String PRODUCER_NAME = "notification";
    private final KafkaProducerConfigProperties config;
    private final KafkaProducerFactory<String, JsonObject> producerFactory;
    private boolean stopped = false;

    /**
     * Creates an instance.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param kafkaProducerConfig The Kafka producer configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedNotificationSender(final KafkaProducerFactory<String, JsonObject> producerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig) {
        Objects.requireNonNull(producerFactory);
        Objects.requireNonNull(kafkaProducerConfig);

        this.producerFactory = producerFactory;
        this.config = kafkaProducerConfig;
    }

    @Override
    public Future<Void> publish(final AbstractNotification notification) {

        Objects.requireNonNull(notification);

        if (stopped) {
            return Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "sender already stopped"));
        }

        try {

            final String topic = NotificationTopicHelper.getTopicName(notification.getClass());
            final String key = getKey(notification);
            final JsonObject value = JsonObject.mapFrom(notification);
            final KafkaProducerRecord<String, JsonObject> record = KafkaProducerRecord.create(topic, key, value);

            final Promise<RecordMetadata> sendPromise = Promise.promise();
            producerFactory.getOrCreateProducer(PRODUCER_NAME, config).send(record, sendPromise);

            return sendPromise.future()
                    .recover(t -> Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t)))
                    .mapEmpty();
        } catch (RuntimeException ex) {
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, ex));
        }
    }

    private String getKey(final AbstractNotification notification) {
        if (notification instanceof TenantChangeNotification) {
            return ((TenantChangeNotification) notification).getTenantId();
        } else if (notification instanceof DeviceChangeNotification) {
            return ((DeviceChangeNotification) notification).getDeviceId();
        } else if (notification instanceof CredentialsChangeNotification) {
            return ((CredentialsChangeNotification) notification).getDeviceId();
        } else {
            throw new IllegalArgumentException("unknown notification type");
        }
    }

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

}
