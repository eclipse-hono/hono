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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * Verifies behavior of {@link ProtonBasedNotificationReceiver}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class ProtonBasedNotificationReceiverTest {

    private HonoConnection connection;
    private ProtonBasedNotificationReceiver client;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        final Tracer tracer = TracingMockSupport.mockTracer(TracingMockSupport.mockSpan());

        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        final RequestResponseClientConfigProperties config = new RequestResponseClientConfigProperties();
        config.setRequestTimeout(0); // don't let requestTimeout timer be started
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, config, tracer);
        when(connection.connect()).thenReturn(Future.succeededFuture(connection));
        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class), VertxMockSupport.anyHandler()))
                .thenReturn(Future.succeededFuture(receiver));
        client = new ProtonBasedNotificationReceiver(connection);
    }

    /**
     * Verifies that the client with a registered consumer receives notification messages.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRegisterCommandConsumer(final VertxTestContext ctx) {

        final String tenantId = "my-tenant";
        final Instant creationTime = Instant.parse("2007-12-03T10:15:30Z");
        final TenantChangeNotification notification = new TenantChangeNotification(LifecycleChange.CREATE,
                tenantId, creationTime, false, false);
        final Message notificationMessage = ProtonHelper.message();
        AmqpUtils.setJsonPayload(notificationMessage, JsonObject.mapFrom(notification));

        // GIVEN a client where a TenantChangeNotification consumer gets registered
        final AtomicReference<TenantChangeNotification> receivedNotificationRef = new AtomicReference<>();
        client.registerConsumer(TenantChangeNotification.TYPE, receivedNotificationRef::set);

        // WHEN starting the client
        client.start()
                .onComplete(ctx.succeeding(v -> {
                    ctx.verify(() -> {
                        // THEN a receiver link got created
                        final ProtonMessageHandler receiverMessageHandler = AmqpClientUnitTestHelper
                                .assertReceiverLinkCreated(connection);
                        // and sending a notification on the link
                        receiverMessageHandler.handle(mock(ProtonDelivery.class), notificationMessage);
                        // causes the client to receive the notification
                        assertThat(receivedNotificationRef.get()).isNotNull();
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the receiver decodes the notifications it receives and invokes the correct handler.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCorrectHandlerIsInvoked(final VertxTestContext ctx) {

        final String tenantId = "my-tenant";
        final String deviceId = "my-device";
        final Instant creationTime = Instant.parse("2007-12-03T10:15:30Z");

        final TenantChangeNotification tenantChangeNotification = new TenantChangeNotification(LifecycleChange.CREATE,
                tenantId, creationTime, false, false);
        final Message tenantChangeNotificationMsg = ProtonHelper.message();
        AmqpUtils.setJsonPayload(tenantChangeNotificationMsg, JsonObject.mapFrom(tenantChangeNotification));

        final DeviceChangeNotification deviceChangeNotification = new DeviceChangeNotification(LifecycleChange.CREATE,
                tenantId, deviceId, creationTime, false);
        final Message deviceChangeNotificationMsg = ProtonHelper.message();
        AmqpUtils.setJsonPayload(deviceChangeNotificationMsg, JsonObject.mapFrom(deviceChangeNotification));

        final CredentialsChangeNotification credentialsChangeNotification = new CredentialsChangeNotification(tenantId,
                deviceId, creationTime);
        final Message credentialsChangeNotificationMsg = ProtonHelper.message();
        AmqpUtils.setJsonPayload(credentialsChangeNotificationMsg, JsonObject.mapFrom(credentialsChangeNotification));

        final AllDevicesOfTenantDeletedNotification allDevicesOfTenantDeletedChangeNotification = new AllDevicesOfTenantDeletedNotification(
                tenantId, creationTime);
        final Message allDevicesOfTenantDeletedChangeNotificationMsg = ProtonHelper.message();
        AmqpUtils.setJsonPayload(allDevicesOfTenantDeletedChangeNotificationMsg, JsonObject.mapFrom(allDevicesOfTenantDeletedChangeNotification));


        final Checkpoint handlerInvokedCheckpoint = ctx.checkpoint(4);

        client.registerConsumer(TenantChangeNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(TenantChangeNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        client.registerConsumer(DeviceChangeNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(DeviceChangeNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        client.registerConsumer(CredentialsChangeNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(CredentialsChangeNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        client.registerConsumer(AllDevicesOfTenantDeletedNotification.TYPE,
                notification -> ctx.verify(() -> {
                    assertThat(notification).isInstanceOf(AllDevicesOfTenantDeletedNotification.class);
                    handlerInvokedCheckpoint.flag();
                }));

        // WHEN starting the client
        client.start()
                .onComplete(ctx.succeeding(v -> ctx.verify(() -> {
                    // THEN the receiver links got created
                    final Map<String, ProtonMessageHandler> receiverMsgHandlersPerAddress = assertReceiverLinkCreated(connection);

                    assertThat(receiverMsgHandlersPerAddress)
                            .containsKey(NotificationAddressHelper.getAddress(TenantChangeNotification.TYPE));
                    assertThat(receiverMsgHandlersPerAddress)
                            .containsKey(NotificationAddressHelper.getAddress(DeviceChangeNotification.TYPE));
                    assertThat(receiverMsgHandlersPerAddress)
                            .containsKey(NotificationAddressHelper.getAddress(CredentialsChangeNotification.TYPE));
                    assertThat(receiverMsgHandlersPerAddress)
                            .containsKey(NotificationAddressHelper.getAddress(AllDevicesOfTenantDeletedNotification.TYPE));

                    final ProtonMessageHandler tenantChangeReceiverMsgHandler = receiverMsgHandlersPerAddress
                            .get(NotificationAddressHelper.getAddress(TenantChangeNotification.TYPE));
                    final ProtonMessageHandler deviceChangeReceiverMsgHandler = receiverMsgHandlersPerAddress
                            .get(NotificationAddressHelper.getAddress(DeviceChangeNotification.TYPE));
                    final ProtonMessageHandler credentialsChangeReceiverMsgHandler = receiverMsgHandlersPerAddress
                            .get(NotificationAddressHelper.getAddress(CredentialsChangeNotification.TYPE));
                    final ProtonMessageHandler allDevicesOfTenantDeletedChangeReceiverMsgHandler = receiverMsgHandlersPerAddress
                            .get(NotificationAddressHelper.getAddress(AllDevicesOfTenantDeletedNotification.TYPE));
                    // and sending notifications on the links
                    tenantChangeReceiverMsgHandler.handle(mock(ProtonDelivery.class), tenantChangeNotificationMsg);
                    deviceChangeReceiverMsgHandler.handle(mock(ProtonDelivery.class), deviceChangeNotificationMsg);
                    credentialsChangeReceiverMsgHandler.handle(mock(ProtonDelivery.class), credentialsChangeNotificationMsg);
                    allDevicesOfTenantDeletedChangeReceiverMsgHandler.handle(mock(ProtonDelivery.class),
                            allDevicesOfTenantDeletedChangeNotificationMsg);
                    // causes the client to receive the notifications and the handlerInvokedCheckpoint to get flagged
                })));

    }

    private static Map<String, ProtonMessageHandler> assertReceiverLinkCreated(final HonoConnection connection) {
        final Map<String, ProtonMessageHandler> resultMap = new HashMap<>();

        final ArgumentCaptor<ProtonMessageHandler> messageHandlerCaptor = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        final ArgumentCaptor<String> addressCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce()).createReceiver(addressCaptor.capture(), any(ProtonQoS.class),
                messageHandlerCaptor.capture(), VertxMockSupport.anyHandler());

        final List<ProtonMessageHandler> handlers = messageHandlerCaptor.getAllValues();
        final List<String> addresses = addressCaptor.getAllValues();
        for (int i = 0; i < handlers.size(); i++) {
            resultMap.put(addresses.get(i), handlers.get(i));
        }
        return resultMap;
    }

}
