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

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import io.vertx.core.eventbus.EventBus;
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

    private Vertx vertx;
    private HonoConnection connection;
    private DownstreamSenderFactoryImpl factory;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), anyHandler())).thenAnswer(invocation -> {
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
        factory = new DownstreamSenderFactoryImpl(connection);
    }

    /**
     * Verifies that a concurrent request to create a sender fails the given future for tracking the attempt if the
     * initial request doesn't complete.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetTelemetrySenderFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a factory that already tries to create a telemetry sender for "tenant" (and never completes doing so)
        final Future<ProtonSender> sender = Future.future();
        when(connection.createSender(anyString(), any(ProtonQoS.class), anyHandler())).thenReturn(sender);
        final Future<DownstreamSender> result = factory.getOrCreateTelemetrySender("telemetry/tenant");
        assertFalse(result.isComplete());

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        factory.getOrCreateTelemetrySender("telemetry/tenant").setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the concurrent attempt fails after having done the default number of retries.
            ctx.assertTrue(ServerErrorException.class.isInstance(t));
            verify(vertx, times(CachingClientFactory.MAX_CREATION_RETRIES)).setTimer(anyLong(), notNull());
        }));
        sender.complete(mock(ProtonSender.class));
        assertTrue(result.isComplete());
    }

    /**
     * Verifies that a request to create a sender is failed immediately when the
     * underlying connection to the server fails.
     */
    @Test
    public void testGetTelemetrySenderFailsOnConnectionFailure() {

        // GIVEN a factory that tries to create a telemetry sender for "tenant"
        final Future<ProtonSender> sender = Future.future();
        when(connection.createSender(anyString(), any(ProtonQoS.class), anyHandler())).thenReturn(sender);
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<DisconnectListener<HonoConnection>> disconnectHandler = ArgumentCaptor.forClass(DisconnectListener.class);
        verify(connection).addDisconnectListener(disconnectHandler.capture());

        final Future<DownstreamSender> result = factory.getOrCreateTelemetrySender("telemetry/tenant");
        assertFalse(result.isComplete());

        // WHEN the underlying connection fails
        disconnectHandler.getValue().onDisconnect(connection);

        // THEN all creation requests are failed
        assertTrue(result.failed());
    }
}
