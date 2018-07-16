/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.cli;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test cases verifying the behavior of {@code Receiver}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class ReceiverTest {

    private Receiver receiver;
    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Setups the receiver with mocks.
     *
     */
    @Before
    public void setup() {
        final HonoClient client = mock(HonoClient.class);
        receiver = new Receiver();
        MockitoAnnotations.initMocks(this);
        when(client.connect(any(Handler.class))).thenReturn(Future.succeededFuture(client));
        when(client.connect()).thenReturn(Future.succeededFuture(client));
        when(client.createTelemetryConsumer(anyString(), any(Consumer.class), any(Handler.class)))
                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        when(client.createEventConsumer(anyString(), any(Consumer.class), any(Handler.class)))
                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        receiver.client = client;
        receiver.vertx = Vertx.vertx();
        receiver.tenantId = "TEST_TENANT";
    }

    /**
     * Cleans up after each test.
     */
    @After
    public void destroy() {
        if (receiver.vertx != null) {
            receiver.vertx.close();
        }
    }

    /**
     * Verifies that the receiver is started successfully with message.type=telemetry.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testTelemetryStart(final TestContext context) {
        receiver.messageType = "telemetry";
        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());
        receiver.start().setHandler(result -> {
            context.assertNotNull(result.result().list());
            context.assertTrue(result.result().list().size() == 1);
            done.complete();
        });
    }

    /**
     * Verifies that the receiver is started successfully with message.type=event.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testEventStart(final TestContext context) {
        receiver.messageType = "event";
        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());
        receiver.start().setHandler(result -> {
            context.assertNotNull(result.result().list());
            context.assertTrue(result.result().list().size() == 1);
            done.complete();
        });
    }

    /**
     * Verifies that the receiver is started successfully with message.type=all.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testDefaultStart(final TestContext context) {
        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());
        receiver.start().setHandler(result -> {
            context.assertNotNull(result.result().list());
            context.assertTrue(result.result().list().size() == 2);
            done.complete();
        });
    }

    /**
     * Verifies that the receiver fails to start when invalid value is passed to message.type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testInvalidTypeStart(final TestContext context) {
        receiver.messageType = "xxxxx";
        receiver.start().setHandler(context.asyncAssertFailure());
    }
}
