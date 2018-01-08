/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link TelemetrySenderImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetrySenderImplTest {

    private Context context;
    private ClientConfigProperties config;
    private ProtonSender sender;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        context = mock(Context.class);
        doAnswer(invocation -> {
            Handler<Void> handler = invocation.getArgumentAt(0, Handler.class);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));
        config = new ClientConfigProperties();
        sender = mock(ProtonSender.class);
    }

    /**
     * Verifies that the sender does not wait for the peer to settle and
     * accept a message before succeeding.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testSendMessageDoesNotWaitForAcceptedOutcome(final TestContext ctx) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);
        MessageSender messageSender = new TelemetrySenderImpl(config, sender, "tenant", "telemetry/tenant", context);
        final AtomicReference<Handler<ProtonDelivery>> handlerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgumentAt(1, Handler.class));
            return mock(ProtonDelivery.class);
        }).when(sender).send(any(Message.class), any(Handler.class));

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text", "token");
        // which gets rejected by the peer
        ProtonDelivery rejected = mock(ProtonDelivery.class);
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
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSendMessageFailsOnLackOfCredit(final TestContext ctx) {

        // GIVEN a sender that has no credit
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);
        MessageSender messageSender = new TelemetrySenderImpl(config, sender, "tenant", "telemetry/tenant", context);

        // WHEN trying to send a message
        final Future<ProtonDelivery> result = messageSender.send("device", "some payload", "application/text", "token");

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }

}
