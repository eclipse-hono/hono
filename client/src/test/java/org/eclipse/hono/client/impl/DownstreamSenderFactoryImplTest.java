/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link DownstreamSenderFactoryImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class DownstreamSenderFactoryImplTest {

    private HonoConnection connection;
    private DownstreamSenderFactoryImpl factory;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        final Vertx vertx = mock(Vertx.class);
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        factory = new DownstreamSenderFactoryImpl(connection);
    }

    /**
     * Verifies that a concurrent request to create a sender fails the given future for tracking the attempt.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTelemetrySenderFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a factory that already tries to create a telemetry sender for "tenant"
        final Future<ProtonSender> sender = Future.future();
        when(connection.createSender(anyString(), any(ProtonQoS.class), any(Handler.class))).thenReturn(sender);
        final Future<DownstreamSender> result = factory.getOrCreateTelemetrySender("telemetry/tenant");
        assertFalse(result.isComplete());

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        factory.getOrCreateTelemetrySender("telemetry/tenant").setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the concurrent attempt fails without any attempt being made to create another sender
                    ctx.assertTrue(ServerErrorException.class.isInstance(t));
                }));
        sender.complete(mock(ProtonSender.class));
        assertTrue(result.isComplete());
    }

    /**
     * Verifies that a request to create a sender is failed immediately when the
     * underlying connection to the server fails.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetTelemetrySenderFailsOnConnectionFailure() {

        // GIVEN a factory that tries to create a telemetry sender for "tenant"
        final Future<ProtonSender> sender = Future.future();
        when(connection.createSender(anyString(), any(ProtonQoS.class), any(Handler.class))).thenReturn(sender);
        final ArgumentCaptor<DisconnectListener> disconnectHandler = ArgumentCaptor.forClass(DisconnectListener.class);
        verify(connection).addDisconnectListener(disconnectHandler.capture());

        final Future<DownstreamSender> result = factory.getOrCreateTelemetrySender("telemetry/tenant");
        assertFalse(result.isComplete());

        // WHEN the underlying connection fails
        disconnectHandler.getValue().onDisconnect(connection);;

        // THEN all creation requests are failed
        assertTrue(result.failed());
    }
}
