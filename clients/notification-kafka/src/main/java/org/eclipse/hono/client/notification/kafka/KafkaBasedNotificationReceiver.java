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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.hono.client.kafka.KafkaClientFactory;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationReceiver;
import org.eclipse.hono.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A client for receiving notifications with Kafka.
 */
public class KafkaBasedNotificationReceiver extends HonoKafkaConsumer<JsonObject> implements NotificationReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedNotificationReceiver.class);
    private static final String NAME = "notification";

    private final Map<Class<? extends AbstractNotification>, Handler<? extends AbstractNotification>> handlerPerType = new HashMap<>();

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
    public KafkaBasedNotificationReceiver(
            final Vertx vertx,
            final NotificationKafkaConsumerConfigProperties consumerConfig) {

        super(vertx, Set.of(), (Pattern) null, consumerConfig.getConsumerConfig(NAME));

        if (!consumerConfig.isConfigured()) {
            throw new IllegalArgumentException("No Kafka configuration found!");
        }
        setPollTimeout(Duration.ofMillis(consumerConfig.getPollTimeout()));
        setConsumerCreationRetriesTimeout(KafkaClientFactory.UNLIMITED_RETRIES_DURATION);
        setRecordHandler(this::handleRecord);
    }

    @Override
    public <T extends AbstractNotification> void registerConsumer(
            final NotificationType<T> notificationType,
            final Handler<T> consumer) {

        if (!lifecycleStatus.isStopped()) {
            throw new IllegalStateException("consumers cannot be added when receiver is already started");
        }

        addTopic(NotificationTopicHelper.getTopicName(notificationType));
        handlerPerType.put(notificationType.getClazz(), consumer);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void handleRecord(final KafkaConsumerRecord<String, JsonObject> record) {
        final JsonObject json = record.value();
        if (LOG.isTraceEnabled()) {
            LOG.trace("received notification:{}{}", System.lineSeparator(), json.encodePrettily());
        }

        final AbstractNotification notification = json.mapTo(AbstractNotification.class);
        final Handler handler = handlerPerType.get(notification.getClass());
        if (handler != null) {
            handler.handle(notification);
        }
    }

    @Override
    public Future<Void> stop() {
        return super.stop()
                .onComplete(v -> handlerPerType.clear())
                .mapEmpty();
    }
}
