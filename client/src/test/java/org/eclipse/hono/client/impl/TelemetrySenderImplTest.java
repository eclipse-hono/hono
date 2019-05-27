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
package org.eclipse.hono.client.impl;

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link TelemetrySenderImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetrySenderImplTest {

    private Vertx vertx;
    private ProtonSender sender;
    private ClientConfigProperties config;
    private HonoConnection connection;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        sender = HonoClientUnitTestHelper.mockProtonSender();
        config = new ClientConfigProperties();
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, config);
    }

    /**
     * Verifies that the sender does not wait for the peer to settle and
     * accept a message before succeeding.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendMessageDoesNotWaitForAcceptedOutcome(final TestContext ctx) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final DownstreamSender messageSender = new TelemetrySenderImpl(connection, sender, "tenant", "telemetry/tenant");
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), anyHandler());

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text");
        // which gets rejected by the peer
        final ProtonDelivery rejected = mock(ProtonDelivery.class);
        when(rejected.remotelySettled()).thenReturn(Boolean.TRUE);
        when(rejected.getRemoteState()).thenReturn(new Rejected());
        handlerRef.get().handle(rejected);

        // THEN the resulting future is succeeded nevertheless
        assertTrue(result.succeeded());
        // and the message has been sent
        verify(sender).send(any(Message.class), eq(handlerRef.get()));
    }

    /**
     * Verifies that the sender fails if no credit is available.
     */
    @Test
    public void testSendAndWaitForOutcomeFailsOnLackOfCredit() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);
        final DownstreamSender messageSender = new TelemetrySenderImpl(connection, sender, "tenant", "telemetry/tenant");

        // WHEN trying to send a message
        final Message event = ProtonHelper.message("telemetry/tenant", "hello");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(event);

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(sender, never()).send(any(Message.class), anyHandler());
    }

    /**
     * Verifies that a timeout occurring while a message is sent doesn't cause the corresponding 
     * OpenTracing span to stay unfinished.
     */
    @Test
    public void testSendMessageFailsOnTimeout() {

        // GIVEN a sender that won't receive a delivery update on sending a message 
        // and directly triggers the timeout handler
        when(sender.send(any(Message.class), anyHandler())).thenReturn(mock(ProtonDelivery.class));
        when(vertx.setTimer(anyLong(), anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            final long timerId = 1;
            handler.handle(timerId);
            return timerId;
        });
        final DownstreamSender messageSender = new TelemetrySenderImpl(connection, sender, "tenant", "telemetry/tenant");

        // WHEN sending a message
        final Message message = mock(Message.class);
        final Span span = mock(Span.class);
        ((TelemetrySenderImpl) messageSender).sendMessage(message, span);

        // THEN the given Span will nonetheless be finished.
        verify(span).finish();
    }
}
