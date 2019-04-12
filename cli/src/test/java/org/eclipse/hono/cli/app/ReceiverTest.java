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

package org.eclipse.hono.cli.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Test cases verifying the behavior of {@code Receiver}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class ReceiverTest {

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    private Receiver receiver;

    /**
     * Sets up the receiver with mocks.
     *
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup() {

        final Vertx vertx = mock(Vertx.class);
        when(vertx.getOrCreateContext()).thenReturn(mock(Context.class));

        final ApplicationClientFactory connection = mock(ApplicationClientFactory.class);
        when(connection.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(connection.createTelemetryConsumer(anyString(), any(Consumer.class), any(Handler.class)))
                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        when(connection.createEventConsumer(anyString(), any(Consumer.class), any(Handler.class)))
                .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));

        receiver = new Receiver();
        receiver.setHonoConnection(connection);
        receiver.setVertx(vertx);
        receiver.tenantId = "TEST_TENANT";
    }

    /**
     * Verifies that the receiver is started successfully with message.type=telemetry.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testTelemetryStart(final TestContext context) {
        receiver.messageType = "telemetry";
        receiver.start().setHandler(context.asyncAssertSuccess(result->{
            context.assertNotNull(result.list());
            context.assertTrue(result.list().size() == 1);
        }));
    }

    /**
     * Verifies that the receiver is started successfully with message.type=event.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testEventStart(final TestContext context) {
        receiver.messageType = "event";
        receiver.start().setHandler(context.asyncAssertSuccess(result->{
            context.assertNotNull(result.list());
            context.assertTrue(result.list().size() == 1);
        }));
    }

    /**
     * Verifies that the receiver is started successfully with message.type=all.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testDefaultStart(final TestContext context) {
        receiver.messageType="all";
        receiver.start().setHandler(context.asyncAssertSuccess(result->{
           context.assertNotNull(result.list());
           context.assertTrue(result.list().size() == 2);
        }));
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
