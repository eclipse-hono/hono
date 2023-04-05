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

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.pubsub.AbstractPubSubBasedMessageSender;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for publishing notifications to Google Pub/Sub.
 */
public class PubSubBasedNotificationSender extends AbstractPubSubBasedMessageSender implements NotificationSender {

    private static final String TOPIC_ENDPOINT = "notification";

    /**
     * Creates a new PubSub-based notification sender.
     *
     * @param publisherFactory The factory to use for creating Pub/Sub publishers.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException If any of the parameters are {@code null}.
     */
    public PubSubBasedNotificationSender(
            final PubSubPublisherFactory publisherFactory,
            final String projectId,
            final Tracer tracer) {
        super(publisherFactory, TOPIC_ENDPOINT, projectId, tracer);
    }

    @Override
    public Future<Void> publish(final AbstractNotification notification) {
        Objects.requireNonNull(notification);

        if (!lifecycleStatus.isStarted()) {
            return Future.failedFuture(
                    new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "sender not started"));
        }
        final String topic = PubSubMessageHelper.getTopicName(TOPIC_ENDPOINT, notification.getType().getAddress());
        return createPubSubMessage(notification)
                .compose(message -> {
                    log.debug("sending notification to Pub/Sub [topic: {}, key: {}]", topic, notification.getKey());
                    return getOrCreatePublisher(topic)
                        .publish(message)
                        .recover(t -> {
                            log.error("error publishing notification to Pub/Sub for notification [{}]", notification, t);
                            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t));
                        });
                })
                .mapEmpty();
    }

    private Future<PubsubMessage> createPubSubMessage(final AbstractNotification notification) {
        try {
            final JsonObject value = JsonObject.mapFrom(notification);
            final ByteString data = ByteString.copyFrom(value.toBuffer().getBytes());

            return Future.succeededFuture(PubsubMessage.newBuilder()
                    .setOrderingKey(notification.getType().getAddress())
                    .setData(data)
                    .build());
        } catch (final RuntimeException e) {
            log.error("error creating Pub/Sub message for notification {}", notification, e);
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, e));
        }
    }
}
