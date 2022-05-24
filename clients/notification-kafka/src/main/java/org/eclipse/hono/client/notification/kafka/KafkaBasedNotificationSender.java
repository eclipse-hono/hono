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

package org.eclipse.hono.client.notification.kafka;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

/**
 * A client for publishing notifications to a Kafka broker.
 */
public class KafkaBasedNotificationSender extends AbstractKafkaBasedMessageSender<JsonObject> implements NotificationSender {

    static final String PRODUCER_NAME = "notification";

    /**
     * Creates an instance.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param config The Kafka producer configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedNotificationSender(
            final KafkaProducerFactory<String, JsonObject> producerFactory,
            final NotificationKafkaProducerConfigProperties config) {
        super(producerFactory, PRODUCER_NAME, config, NoopTracerFactory.create());
    }

    @Override
    public Future<Void> publish(final AbstractNotification notification) {

        Objects.requireNonNull(notification);

        if (!lifecycleStatus.isStarted()) {
            return Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "sender not started"));
        }

        return createProducerRecord(notification)
                .compose(record -> getOrCreateProducer().send(record)
                        .recover(t -> {
                            log.debug("error publishing notification [{}]", notification, t);
                            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t));
                        }))
                .mapEmpty();
    }

    private Future<KafkaProducerRecord<String, JsonObject>> createProducerRecord(final AbstractNotification notification) {
        try {
            final String topic = NotificationTopicHelper.getTopicName(notification.getType());
            final String key = notification.getKey();
            final JsonObject value = JsonObject.mapFrom(notification);

            return Future.succeededFuture(KafkaProducerRecord.create(topic, key, value));
        } catch (final RuntimeException ex) {
            log.error("error creating producer record for notification [{}]", notification, ex);
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, ex));
        }
    }
}
