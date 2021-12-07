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

package org.eclipse.hono.client.notification.kafka;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

/**
 * A client for publishing notifications with Kafka.
 */
public class KafkaBasedNotificationSender implements NotificationSender {

    public static final String PRODUCER_NAME = "notification";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedNotificationSender.class);

    private final NotificationKafkaProducerConfigProperties config;
    private final KafkaProducerFactory<String, JsonObject> producerFactory;
    private boolean stopped = false;

    /**
     * Creates an instance.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param config The Kafka producer configuration properties to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedNotificationSender(final KafkaProducerFactory<String, JsonObject> producerFactory,
            final NotificationKafkaProducerConfigProperties config) {
        this.producerFactory = Objects.requireNonNull(producerFactory);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Future<Void> publish(final AbstractNotification notification) {

        Objects.requireNonNull(notification);

        if (stopped) {
            return Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "sender already stopped"));
        }

        return createProducerRecord(notification)
                .compose(record -> {
                    final var producer = producerFactory.getOrCreateProducer(PRODUCER_NAME, config);
                    return producer.send(record)
                            .recover(t -> {
                                LOG.debug("error publishing notification [{}]", notification, t);
                                return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t));
                            });
                }).mapEmpty();
    }

    private Future<KafkaProducerRecord<String, JsonObject>> createProducerRecord(final AbstractNotification notification) {
        try {
            final String topic = NotificationTopicHelper.getTopicName(notification.getClass());
            final String key = getKey(notification);
            final JsonObject value = JsonObject.mapFrom(notification);

            return Future.succeededFuture(KafkaProducerRecord.create(topic, key, value));
        } catch (final RuntimeException ex) {
            LOG.error("error creating producer record for notification [{}]", notification, ex);
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, ex));
        }
    }

    private String getKey(final AbstractNotification notification) {
        if (notification instanceof TenantChangeNotification) {
            return ((TenantChangeNotification) notification).getTenantId();
        } else if (notification instanceof DeviceChangeNotification) {
            return ((DeviceChangeNotification) notification).getDeviceId();
        } else if (notification instanceof CredentialsChangeNotification) {
            return ((CredentialsChangeNotification) notification).getDeviceId();
        } else if (notification instanceof AllDevicesOfTenantDeletedNotification) {
            return ((AllDevicesOfTenantDeletedNotification) notification).getTenantId();
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
