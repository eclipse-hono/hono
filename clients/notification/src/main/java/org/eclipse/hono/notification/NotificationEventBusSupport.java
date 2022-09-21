/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.notification;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.tracing.TracingPolicy;

/**
 * Support for sending and receiving notifications via the vert.x event bus.
 */
public class NotificationEventBusSupport {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationEventBusSupport.class);

    private static final String NOTIFICATION_EVENT_BUS_CONTAINER_LOCAL_CODEC = "NotificationEventBusContainerLocalCodec";

    private static final Map<Vertx, Boolean> VERTX_INSTANCES_WITH_REGISTERED_CODEC_MAP = Collections
            .synchronizedMap(new WeakHashMap<>());

    private NotificationEventBusSupport() {
        // prevent instantiation
    }

    /**
     * Registers a consumer to receive notifications via the vert.x event bus.
     *
     * @param vertx The vert.x instance to use.
     * @param notificationType The type of notifications to register the consumer for.
     * @param consumer The consumer.
     * @param <T> The class of notifications handled by the given consumer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static <T extends AbstractNotification> void registerConsumer(final Vertx vertx,
            final NotificationType<T> notificationType, final Handler<T> consumer) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(notificationType);
        Objects.requireNonNull(consumer);

        vertx.eventBus().<T>consumer(
                getEventBusAddress(notificationType),
                msg -> {
                    final T notification = msg.body();
                    if (notification != null && notificationType.equals(notification.getType())) {
                        consumer.handle(notification);
                    } else {
                        LOG.warn("got event bus message with unexpected body on address [{}]", getEventBusAddress(notificationType));
                    }
                });
    }

    /**
     * Gets a handler that sends a given notification via the vert.x event bus.
     * <p>
     * Applying {@code null} on the returned handler will throw a {@link NullPointerException}.
     *
     * @param vertx The vert.x instance to use.
     * @param <T> The class of the notifications to send.
     * @return A handler that sends a given notification.
     * @throws NullPointerException if vertx is {@code null}.
     */
    public static <T extends AbstractNotification> Handler<T> getNotificationSender(final Vertx vertx) {
        Objects.requireNonNull(vertx);

        if (VERTX_INSTANCES_WITH_REGISTERED_CODEC_MAP.putIfAbsent(vertx, true) == null) {
            vertx.eventBus().registerCodec(new NotificationEventBusContainerLocalCodec());
        }
        return (notification) -> sendNotification(vertx, notification);
    }

    private static <T extends AbstractNotification> void sendNotification(final Vertx vertx, final T notification) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(notification);

        final DeliveryOptions options = new DeliveryOptions();
        options.setCodecName(NOTIFICATION_EVENT_BUS_CONTAINER_LOCAL_CODEC);
        options.setLocalOnly(true);
        options.setTracingPolicy(TracingPolicy.IGNORE);
        vertx.eventBus().publish(getEventBusAddress(notification.getType()), notification, options);
    }

    /**
     * Gets the event bus address on which notifications of the given type get sent.
     *
     * @param notificationType The notification type for which to get the address.
     * @param <T> The notification class associated with the given type.
     * @return The address.
     */
    public static <T extends AbstractNotification> String getEventBusAddress(final NotificationType<T> notificationType) {
        return Constants.EVENT_BUS_ADDRESS_NOTIFICATION_PREFIX + notificationType.getTypeName();
    }

    /**
     * Codec for {@link AbstractNotification} objects sent via the vert.x event bus.
     * <p>
     * Only supports local delivery.
     */
    static class NotificationEventBusContainerLocalCodec
            implements MessageCodec<AbstractNotification, AbstractNotification> {

        @Override
        public void encodeToWire(final Buffer buffer, final AbstractNotification t) {
            throw new UnsupportedOperationException(name() + " can only be used for local delivery");
        }

        @Override
        public AbstractNotification decodeFromWire(final int pos, final Buffer buffer) {
            throw new UnsupportedOperationException(name() + " can only be used for local delivery");
        }

        @Override
        public AbstractNotification transform(final AbstractNotification instance) {
            return instance;
        }

        @Override
        public String name() {
            return NOTIFICATION_EVENT_BUS_CONTAINER_LOCAL_CODEC;
        }

        @Override
        public byte systemCodecID() {
            return -1;
        }
    }
}
