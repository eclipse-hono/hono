/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonSender;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link EventSenderImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class EventSenderImplTest {

    private Vertx vertx;
    private Context context;
    private ProtonSender sender;

    private ClientConfigProperties config;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        context = HonoClientUnitTestHelper.mockContext(vertx);
        sender = HonoClientUnitTestHelper.mockProtonSender();

        config = new ClientConfigProperties();
    }

    /**
     * Verifies that the sender waits for the peer to settle and
     * accept a message before succeeding the returned future.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testSendMessageWaitsForAcceptedOutcome(final TestContext ctx) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "telemetry/tenant", context);
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), any(Handler.class));

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text", "token");

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
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testSendMessageFailsForRejectedOutcome(final TestContext ctx) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "telemetry/tenant", context);
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), any(Handler.class));

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text", "token");

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
    @SuppressWarnings("unchecked")
    @Test
    public void testSendAndWaitForOutcomeFailsOnLackOfCredit() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);
        final MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "event/tenant", context);

        // WHEN trying to send a message
        final Message event = ProtonHelper.message("event/tenant", "hello");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(event);

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }

    /**
     * Verifies that the sender marks messages as durable.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testSendMarksMessageAsDurable(final TestContext ctx) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "telemetry/tenant", context);
        when(sender.send(any(Message.class), any(Handler.class))).thenReturn(mock(ProtonDelivery.class));

        // WHEN trying to send a message
        final Message msg = ProtonHelper.message("telemetry/tenant/deviceId", "some payload");
        messageSender.send(msg);

        // THEN the message has been sent
        verify(sender).send(any(Message.class), any(Handler.class));
        // and the message has been marked as durable
        assertTrue(msg.isDurable());
    }

    /**
     * Verifies that the sender marks messages as durable.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testSendAndWaitForOutcomeMarksMessageAsDurable(final TestContext ctx) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        final MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "telemetry/tenant", context);
        when(sender.send(any(Message.class), any(Handler.class))).thenReturn(mock(ProtonDelivery.class));

        // WHEN trying to send a message
        final Message msg = ProtonHelper.message("telemetry/tenant/deviceId", "some payload");
        messageSender.sendAndWaitForOutcome(msg);

        // THEN the message has been sent
        verify(sender).send(any(Message.class), any(Handler.class));
        // and the message has been marked as durable
        assertTrue(msg.isDurable());
    }

}
