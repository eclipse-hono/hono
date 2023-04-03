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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberClient;
import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberFactory;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.cloud.pubsub.v1.MessageReceiver;

import io.vertx.core.Future;

/**
 * Verifies the behavior of {@link PubSubBasedNotificationReceiver}.
 */
public class PubSubBasedNotificationReceiverTest {

    private PubSubSubscriberFactory factory;
    private PubSubBasedNotificationReceiver receiver;
    private PubSubSubscriberClient subscriber;
    private MessageReceiver messageReceiver;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    void setUp() {
        this.subscriber = mock(PubSubSubscriberClient.class);
        this.factory = mock(PubSubSubscriberFactory.class);
        this.messageReceiver = mock(MessageReceiver.class);
        this.receiver = new PubSubBasedNotificationReceiver(factory, messageReceiver);
    }

    /**
     * Verifies that the subscriber is successfully created by the receiver.
     */
    @Test
    public void testCreateSubscriberAndSubscribe() {
        when(subscriber.subscribe(true)).thenReturn(Future.succeededFuture());
        when(factory.getOrCreateSubscriber("registry-tenant.notification", messageReceiver)).thenReturn(subscriber);
        receiver.registerConsumer(TenantChangeNotification.TYPE, notification -> {
        });
        verify(subscriber).subscribe(true);
    }

    /**
     * Verifies that the subscriber is successfully created by the receiver, but throws an exception on subscribing.
     */
    @Test
    public void testSubscriptionIsFailing() {
        when(subscriber.subscribe(true)).thenReturn(Future.failedFuture("error"));
        when(factory.getOrCreateSubscriber("registry-tenant.notification", messageReceiver)).thenReturn(subscriber);

        assertThrows(IllegalStateException.class,
                () -> receiver.registerConsumer(TenantChangeNotification.TYPE, notification -> {
                }));
        verify(subscriber).subscribe(true);
    }

}
