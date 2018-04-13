/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.client.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
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
        MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "telemetry/tenant", context);
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
        ProtonDelivery accepted = mock(ProtonDelivery.class);
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
        MessageSender messageSender = new EventSenderImpl(config, sender, "tenant", "telemetry/tenant", context);
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
        ProtonDelivery rejected = mock(ProtonDelivery.class);
        when(rejected.remotelySettled()).thenReturn(Boolean.TRUE);
        when(rejected.getRemoteState()).thenReturn(new Rejected());
        handlerRef.get().handle(rejected);

        assertFalse(result.succeeded());
    }
}
