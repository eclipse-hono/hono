/*
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.notification.amqp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.amqp.AbstractServiceClient;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationReceiver;
import org.eclipse.hono.notification.NotificationType;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A vertx-proton based client for receiving notifications.
 */
public class ProtonBasedNotificationReceiver extends AbstractServiceClient implements NotificationReceiver {

    private static final int RECREATE_CONSUMERS_DELAY_MILLIS = 20;

    /**
     * Cache key used here is the link address.
     */
    private final CachingClientFactory<ProtonReceiver> receiverFactory;

    private final AtomicBoolean recreatingConsumers = new AtomicBoolean(false);
    private final AtomicBoolean tryAgainRecreatingConsumers = new AtomicBoolean(false);
    private final Map<Class<? extends AbstractNotification>, Handler<? extends AbstractNotification>> handlerPerType = new HashMap<>();
    private final Set<String> addresses = new HashSet<>();
    private final AtomicBoolean startCalled = new AtomicBoolean();
    private final AtomicBoolean stopCalled = new AtomicBoolean();

    /**
     * Creates a client for consuming notifications.
     *
     * @param connection The connection to the AMQP network.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedNotificationReceiver(final HonoConnection connection) {
        super(connection, SendMessageSampler.Factory.noop());
        receiverFactory = new CachingClientFactory<>(connection.getVertx(), c -> true);
    }

    @Override
    public Future<Void> start() {
        if (!startCalled.compareAndSet(false, true)) {
            return Future.succeededFuture();
        }
        return connectOnStart()
                .onComplete(v -> {
                    if (addresses.isEmpty()) {
                        log.warn("no notification consumers registered - nothing to do");
                    } else {
                        connection.addReconnectListener(c -> recreateConsumers());
                        // trigger creation of notification consumer links (with retry if failed)
                        recreateConsumers();
                    }
                });
    }

    @Override
    public Future<Void> stop() {
        if (!stopCalled.compareAndSet(false, true)) {
            return Future.succeededFuture();
        }
        addresses.clear();
        handlerPerType.clear();
        return disconnectOnStop();
    }

    @Override
    protected void onDisconnect() {
        receiverFactory.onDisconnect();
    }

    @Override
    public <T extends AbstractNotification> void registerConsumer(final NotificationType<T> notificationType,
            final Handler<T> consumer) {

        if (startCalled.get()) {
            throw new IllegalStateException("consumers cannot be added when consumer is already started");
        }

        // Note that different notification types may use the same address!
        final String address = NotificationAddressHelper.getAddress(notificationType);
        addresses.add(address);
        handlerPerType.put(notificationType.getClazz(), consumer);
        log.debug("registered notification receiver [type: {}; address: {}]", notificationType.getClazz().getSimpleName(), address);
    }

    private void recreateConsumers() {
        if (recreatingConsumers.compareAndSet(false, true)) {
            log.debug("recreate notification consumer links");
            connection.isConnected(getDefaultConnectionCheckTimeout())
                    .compose(res -> {
                        final List<Future<ProtonReceiver>> consumerCreationFutures = new ArrayList<>();
                        addresses.forEach(address -> consumerCreationFutures.add(createNotificationConsumerIfNeeded(address)));
                        return Future.join(consumerCreationFutures);
                    }).onComplete(ar -> {
                        recreatingConsumers.set(false);
                        if (tryAgainRecreatingConsumers.compareAndSet(true, false) || ar.failed()) {
                            if (ar.succeeded()) {
                                // tryAgainRecreatingConsumers was set - try again immediately
                                recreateConsumers();
                            } else {
                                invokeRecreateConsumersWithDelay();
                            }
                        }
                    });
        } else {
            // if recreateConsumers() was triggered by a remote link closing, that might have occurred after that link was dealt with above;
            // therefore be sure recreateConsumers() gets called again once the current invocation has finished.
            log.debug("already recreating consumers");
            tryAgainRecreatingConsumers.set(true);
        }
    }

    private void invokeRecreateConsumersWithDelay() {
        connection.getVertx().setTimer(RECREATE_CONSUMERS_DELAY_MILLIS, tid -> recreateConsumers());
    }

    private Future<ProtonReceiver> createNotificationConsumerIfNeeded(final String address) {
        return connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    receiverFactory.getOrCreateClient(address,
                            () -> createProtonReceiver(address),
                            result);
                }));
    }

    private Future<ProtonReceiver> createProtonReceiver(final String address) {
        log.debug("creating new notification receiver link [address: {}]", address);
        return connection.createReceiver(
                address,
                ProtonQoS.AT_LEAST_ONCE,
                getProtonMessageHandler(address),
                sourceAddress -> { // remote close hook
                    log.debug("notification receiver link [address: {}] closed remotely", address);
                    receiverFactory.removeClient(address);
                    invokeRecreateConsumersWithDelay();
                }).onSuccess(receiver -> {
            log.debug("successfully created notification receiver link [address: {}]", address);
        }).onFailure(t -> {
            log.debug("failed to create notification receiver link [address: {}]", address, t);
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ProtonMessageHandler getProtonMessageHandler(final String address) {
        return (delivery, message) -> {
            final Buffer payload = AmqpUtils.getPayload(message);
            if (payload != null) {
                final JsonObject json = payload.toJsonObject();
                if (log.isTraceEnabled()) {
                    log.trace("received notification:{}{}", System.lineSeparator(), json.encodePrettily());
                }
                final AbstractNotification notification = json.mapTo(AbstractNotification.class);
                final String expectedAddress = NotificationAddressHelper.getAddress(notification.getType());
                if (!address.equals(expectedAddress)) {
                    log.warn("got notification of type [{}] on unexpected address [{}]; expected address is [{}]",
                            notification.getClass(), address, expectedAddress);
                }
                final Handler handler = handlerPerType.get(notification.getClass());
                if (handler != null) {
                    handler.handle(notification);
                }
            }
        };
    }
}
