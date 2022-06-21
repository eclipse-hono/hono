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

package org.eclipse.hono.client.notification.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationConstants;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of {@link ProtonBasedNotificationSender}.
 */
@ExtendWith(VertxExtension.class)
public class ProtonBasedNotificationSenderTest {
    private ProtonBasedNotificationSender notificationSender;
    private ProtonSender sender;
    private ProtonDelivery protonDelivery;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        final var vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
        final HonoConnection connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx);

        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(
                anyString(),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(receiver));

        protonDelivery = mock(ProtonDelivery.class);
        when(protonDelivery.remotelySettled()).thenReturn(true);
        when(protonDelivery.getRemoteState()).thenReturn(new Accepted());

        sender = AmqpClientUnitTestHelper.mockProtonSender();
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(protonDelivery);
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));

        notificationSender = new ProtonBasedNotificationSender(connection);
    }

    /**
     * Verifies that the sender sends a notification message with the expected format.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPublishNotificationSucceeds(final VertxTestContext ctx) {
        final String tenantId = "my-tenant";
        final Instant creationTime = Instant.parse("2007-12-03T10:15:30Z");
        final TenantChangeNotification notification = new TenantChangeNotification(LifecycleChange.CREATE,
                tenantId, creationTime, false, false);

        final ArgumentCaptor<Handler<ProtonDelivery>> dispositionHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // WHEN publishing a notification
        final Future<Void> publishNotificationFuture = notificationSender
                .publish(notification)
                .onComplete(ctx.succeedingThenComplete());

        // VERIFY that the message is being sent
        verify(sender).send(messageCaptor.capture(), dispositionHandlerCaptor.capture());

        // VERIFY that the future waits for the disposition to be updated by the peer
        assertThat(publishNotificationFuture.isComplete()).isFalse();

        // THEN the disposition is updated and the peer accepts the message
        dispositionHandlerCaptor.getValue().handle(protonDelivery);

        // VERIFY that the message is as expected
        final Message message = messageCaptor.getValue();
        assertThat(message.getAddress()).isEqualTo(NotificationAddressHelper.getAddress(notification.getType()));
        assertThat(message.getContentType()).isEqualTo(NotificationConstants.CONTENT_TYPE);
        final AbstractNotification decodedNotification = Json.decodeValue(AmqpUtils.getPayload(message),
                AbstractNotification.class);
        assertThat(decodedNotification.getClass()).isEqualTo(notification.getClass());
        assertThat(publishNotificationFuture.isComplete()).isTrue();
    }

}
