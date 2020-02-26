/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.cli.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.eclipse.hono.cli.application.Receiver;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
/**
 * Test cases verifying the behavior of {@code Receiver}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ReceiverTest {

    private Receiver receiver;
    public ClientConfig clientConfig;

    /**
     * Sets up the receiver with mocks.
     *
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        final Vertx vertx = mock(Vertx.class);
        when(vertx.getOrCreateContext()).thenReturn(mock(Context.class));

        final ApplicationClientFactory connection = mock(ApplicationClientFactory.class);
        clientConfig = mock(ClientConfig.class);
        when(connection.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(connection.createTelemetryConsumer(anyString(), any(Consumer.class), any(Handler.class)))
                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        when(connection.createEventConsumer(anyString(), any(Consumer.class), any(Handler.class)))
                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        clientConfig.tenantId = "TEST_TENANT";
        clientConfig.messageType = "all";
        receiver = new Receiver(connection,vertx,clientConfig);
    }

    /**
     * Verifies that the receiver is started successfully with message.type=telemetry.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testTelemetryStart(final VertxTestContext context) {

        receiver.start(new CountDownLatch(1)).setHandler(
                context.succeeding(result ->{
                   context.verify(()->{
                       assertNotNull(result.list());
                       assertTrue(result.succeeded(0));
                   });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the receiver is started successfully with message.type=event.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testEventStart(final VertxTestContext context) {
        clientConfig.messageType = "event";
        receiver.start(new CountDownLatch(1)).setHandler(
                context.succeeding(result -> {
                    context.verify(() -> {
                        assertNotNull(result.list());
                        assertEquals(result.size(), 1);
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the receiver is started successfully with message.type=all.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testDefaultStart(final VertxTestContext context) {
        clientConfig.messageType="all";

        receiver.start(new CountDownLatch(1)).setHandler(
                context.succeeding(result -> {
                    context.verify(() -> {
                        assertNotNull(result.list());
                        assertEquals(result.size(), 2);
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the receiver fails to start when invalid value is passed to message.type.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testInvalidTypeStart(final VertxTestContext context) {
        clientConfig.messageType = "xxxxx";
        receiver.start(new CountDownLatch(1)).setHandler(
                context.failing(result -> context.completeNow()));
    }
}
