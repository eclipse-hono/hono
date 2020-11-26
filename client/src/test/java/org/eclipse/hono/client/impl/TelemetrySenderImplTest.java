/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link TelemetrySenderImpl}.
 *
 */
public class TelemetrySenderImplTest {

    private Vertx vertx;
    private ProtonSender sender;
    private ClientConfigProperties config;
    private HonoConnection connection;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        vertx = mock(Vertx.class);
        sender = HonoClientUnitTestHelper.mockProtonSender();
        config = new ClientConfigProperties();
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, config);
    }

    /**
     * Verifies that the sender does not wait for the peer to settle and
     * accept a message before succeeding.
     */
    @Test
    public void testSendMessageDoesNotWaitForAcceptedOutcome() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final DownstreamSender messageSender = new TelemetrySenderImpl(connection, sender, "tenant", "telemetry/tenant", SendMessageSampler.noop());
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), VertxMockSupport.anyHandler());

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
        final DownstreamSender messageSender = new TelemetrySenderImpl(connection, sender, "tenant", "telemetry/tenant", SendMessageSampler.noop());

        // WHEN trying to send a message
        final Message event = ProtonHelper.message("telemetry/tenant", "hello");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(event);

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that a timeout occurring while a message is sent doesn't cause the corresponding 
     * OpenTracing span to stay unfinished.
     */
    @Test
    public void testSendMessageFailsOnTimeout() {

        // GIVEN a sender that won't receive a delivery update on sending a message 
        // and directly triggers the timeout handler
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(mock(ProtonDelivery.class));
        VertxMockSupport.runTimersImmediately(vertx);
        final DownstreamSender messageSender = new TelemetrySenderImpl(connection, sender, "tenant", "telemetry/tenant", SendMessageSampler.noop());

        // WHEN sending a message
        final Message message = mock(Message.class);
        final Span span = mock(Span.class);
        ((TelemetrySenderImpl) messageSender).sendMessage(message, span);

        // THEN the given Span will nonetheless be finished.
        verify(span).finish();
    }
}
