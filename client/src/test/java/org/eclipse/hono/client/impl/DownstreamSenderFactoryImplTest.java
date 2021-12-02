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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link DownstreamSenderFactoryImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
public class DownstreamSenderFactoryImplTest {

    private Vertx vertx;
    private HonoConnection connection;
    private DownstreamSenderFactoryImpl factory;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        vertx = mock(Vertx.class);
        VertxMockSupport.runTimersImmediately(vertx);
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
        factory = new DownstreamSenderFactoryImpl(connection, SendMessageSampler.Factory.noop());
    }

    /**
     * Verifies that a concurrent request to create a sender fails the given future for tracking the attempt if the
     * initial request doesn't complete.
     *
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetTelemetrySenderFailsIfInvokedConcurrently(final VertxTestContext ctx) {

        // GIVEN a factory that already tries to create a telemetry sender for "tenant" (and never completes doing so)
        final Promise<ProtonSender> sender = Promise.promise();
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
        .thenReturn(sender.future());
        final Future<DownstreamSender> result = factory.getOrCreateTelemetrySender("telemetry/tenant");
        assertThat(result.isComplete()).isFalse();

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        factory.getOrCreateTelemetrySender("telemetry/tenant").onComplete(ctx.failing(t -> {
            // THEN the concurrent attempt fails after having done the default number of retries.
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(ServerErrorException.class);
                verify(vertx, times(CachingClientFactory.MAX_CREATION_RETRIES)).setTimer(anyLong(), notNull());
            });
        }));
        sender.complete(mock(ProtonSender.class));
        ctx.verify(() -> assertThat(result.isComplete()).isTrue());
        ctx.completeNow();
    }

    /**
     * Verifies that a request to create a sender is failed immediately when the
     * underlying connection to the server fails.
     */
    @Test
    public void testGetTelemetrySenderFailsOnConnectionFailure() {

        // GIVEN a factory that tries to create a telemetry sender for "tenant"
        final Promise<ProtonSender> sender = Promise.promise();
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
            .thenReturn(sender.future());
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<DisconnectListener<HonoConnection>> disconnectHandler = ArgumentCaptor.forClass(DisconnectListener.class);
        verify(connection).addDisconnectListener(disconnectHandler.capture());

        final Future<DownstreamSender> result = factory.getOrCreateTelemetrySender("telemetry/tenant");
        assertThat(result.isComplete()).isFalse();

        // WHEN the underlying connection fails
        disconnectHandler.getValue().onDisconnect(connection);

        // THEN all creation requests are failed
        assertThat(result.failed()).isTrue();
    }
}
