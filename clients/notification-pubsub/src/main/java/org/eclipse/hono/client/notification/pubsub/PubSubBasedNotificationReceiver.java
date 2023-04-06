/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.notification.pubsub;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberFactory;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationReceiver;
import org.eclipse.hono.notification.NotificationType;
import org.eclipse.hono.util.LifecycleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/**
 * A client for receiving notifications with Pub/Sub.
 */
public class PubSubBasedNotificationReceiver implements NotificationReceiver {

    private static final String TOPIC_ENDPOINT = "notification";
    private static final Logger log = LoggerFactory.getLogger(PubSubBasedNotificationReceiver.class);
    private final Map<Class<? extends AbstractNotification>, Handler<? extends AbstractNotification>> handlerPerType = new HashMap<>();
    private final PubSubSubscriberFactory factory;
    private final LifecycleStatus lifecycleStatus = new LifecycleStatus();
    private final Set<String> notificationTypes = new HashSet<>();
    private final MessageReceiver receiver;

    /**
     * Creates a client for consuming notifications.
     *
     * @param factory The factory to use for creating Pub/Sub subscribers.
     * @throws NullPointerException If factory is {@code null}.
     */
    public PubSubBasedNotificationReceiver(final PubSubSubscriberFactory factory) {
        this.factory = Objects.requireNonNull(factory);
        this.receiver = (pubsubMessage, ackReplyConsumer) -> {
            handleMessage(pubsubMessage);
            ackReplyConsumer.ack();
        };
    }

    /**
     * Creates a client for consuming notifications. To be used for unittests.
     *
     * @param factory The factory to use for creating Pub/Sub subscribers.
     * @param receiver The receiver to be called when messages are received.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    protected PubSubBasedNotificationReceiver(final PubSubSubscriberFactory factory, final MessageReceiver receiver) {
        this.factory = Objects.requireNonNull(factory);
        this.receiver = Objects.requireNonNull(receiver);
    }

    @Override
    public <T extends AbstractNotification> void registerConsumer(final NotificationType<T> notificationType,
            final Handler<T> consumer) {
        if (notificationTypes.contains(notificationType.getAddress())) {
            log.debug("Notification receiver {} is already registered", notificationType.getAddress());
            return;
        }
        notificationTypes.add(notificationType.getAddress());

        handlerPerType.put(notificationType.getClazz(), consumer);
        final String subscriptionId = PubSubMessageHelper.getTopicName(TOPIC_ENDPOINT, notificationType.getAddress());
        factory.getOrCreateSubscriber(subscriptionId, receiver)
                .subscribe(true)
                .onFailure(t -> {
                    log.error("Error subscribing for notification {}", notificationType.getAddress(), t);
                    throw new IllegalStateException("Error, can not subscribe for notification", t);
                });
    }

    @Override
    public Future<Void> start() {
        if (lifecycleStatus.isStarting()) {
            log.debug("Pub/Sub based notification receiver already started");
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("Consumer is already started/stopping"));
        }
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        return factory.closeAllSubscribers();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void handleMessage(final PubsubMessage message) {
        final Buffer buffer = Buffer.buffer(PubSubMessageHelper.getPayload(message));
        try {
            final JsonObject json = buffer.toJsonObject();

            if (log.isTraceEnabled()) {
                log.trace("received notification: {}{}", System.lineSeparator(), json.encodePrettily());
            }

            final AbstractNotification notification = json.mapTo(AbstractNotification.class);
            final Handler handler = handlerPerType.get(notification.getClass());
            if (handler != null) {
                handler.handle(notification);
            }
        } catch (RuntimeException e) {
            log.debug("Could not handle Pub/Sub message notification, buffer is empty");
        }
    }
}
