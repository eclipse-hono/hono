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

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link EventSenderImpl}.
 *
 */
public class EventSenderImplTest {

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
     * Verifies that the sender waits for the peer to settle and
     * accept a message before succeeding the returned future.
     */
    @Test
    public void testSendMessageWaitsForAcceptedOutcome() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final DownstreamSender messageSender = new EventSenderImpl(connection, sender, "tenant", "telemetry/tenant");
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), VertxMockSupport.anyHandler());

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text");

        // THEN the message has been sent
        // and the result is not completed yet
        verify(sender).send(any(Message.class), eq(handlerRef.get()));
        assertFalse(result.isComplete());

        // until it gets accepted by the peer
        final ProtonDelivery accepted = mock(ProtonDelivery.class);
        when(accepted.remotelySettled()).thenReturn(Boolean.TRUE);
        when(accepted.getRemoteState()).thenReturn(new Accepted());
        handlerRef.get().handle(accepted);

        assertTrue(result.succeeded());
    }

    /**
     * Verifies that the sender fails if the peer does not accept a message.
     */
    @Test
    public void testSendMessageFailsForRejectedOutcome() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final DownstreamSender messageSender = new EventSenderImpl(connection, sender, "tenant", "telemetry/tenant");
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), VertxMockSupport.anyHandler());

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text");

        // THEN the message has been sent
        // and the result is not completed yet
        verify(sender).send(any(Message.class), eq(handlerRef.get()));
        assertFalse(result.isComplete());

        // and the result fails once the peer rejects the message
        final ProtonDelivery rejected = mock(ProtonDelivery.class);
        when(rejected.remotelySettled()).thenReturn(Boolean.TRUE);
        when(rejected.getRemoteState()).thenReturn(new Rejected());
        handlerRef.get().handle(rejected);

        assertFalse(result.succeeded());
    }

    /**
     * Verifies that the sender fails if no credit is available.
     */
    @Test
    public void testSendAndWaitForOutcomeFailsOnLackOfCredit() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);
        final DownstreamSender messageSender = new EventSenderImpl(connection, sender, "tenant", "event/tenant");

        // WHEN trying to send a message
        final Message event = ProtonHelper.message("event/tenant", "hello");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(event);

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the sender marks messages as durable.
     */
    @Test
    public void testSendMarksMessageAsDurable() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final DownstreamSender messageSender = new EventSenderImpl(connection, sender, "tenant", "telemetry/tenant");
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(mock(ProtonDelivery.class));

        // WHEN trying to send a message
        final Message msg = ProtonHelper.message("telemetry/tenant/deviceId", "some payload");
        messageSender.send(msg);

        // THEN the message has been sent
        verify(sender).send(any(Message.class), VertxMockSupport.anyHandler());
        // and the message has been marked as durable
        assertTrue(msg.isDurable());
    }

    /**
     * Verifies that the sender marks messages as durable.
     */
    @Test
    public void testSendAndWaitForOutcomeMarksMessageAsDurable() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final DownstreamSender messageSender = new EventSenderImpl(connection, sender, "tenant", "telemetry/tenant");
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(mock(ProtonDelivery.class));

        // WHEN trying to send a message
        final Message msg = ProtonHelper.message("telemetry/tenant/deviceId", "some payload");
        messageSender.sendAndWaitForOutcome(msg);

        // THEN the message has been sent
        verify(sender).send(any(Message.class), VertxMockSupport.anyHandler());
        // and the message has been marked as durable
        assertTrue(msg.isDurable());
    }

}
