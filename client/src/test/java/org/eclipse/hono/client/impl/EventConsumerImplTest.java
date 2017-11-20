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

import static org.mockito.Mockito.*;

import java.util.function.BiConsumer;

import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;


/**
 * Test cases verifying the behavior of {@link EventConsumerImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class EventConsumerImplTest {

    private Vertx vertx;

    /**
     * Initializes fixture.
     */
    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    /**
     * Cleans up fixture.
     */
    @After
    public void shutDown() {
        vertx.close();
    }

    /**
     * Verifies that the message delivery for a received event is forwarded to the
     * registered event consumer.
     * 
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateRegistersBiConsumerAsMessageHandler(final TestContext ctx) {

        // GIVEN an event consumer that releases all messages
        BiConsumer<ProtonDelivery, Message> eventConsumer = (delivery, message) -> {
            ProtonHelper.released(delivery, true);
        };
        Source source = mock(Source.class);
        when(source.toString()).thenReturn("event/tenant");
        ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteSource()).thenReturn(source);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        ProtonConnection con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(receiver);
        when(receiver.openHandler(any(Handler.class))).thenAnswer(invocation -> {
            invocation.getArgumentAt(0, Handler.class).handle(Future.succeededFuture(receiver));
            return receiver;
        });
        Async consumerCreation = ctx.async();
        EventConsumerImpl.create(vertx.getOrCreateContext(), con, "tenant", new ClientConfigProperties().getInitialCredits(), eventConsumer, ctx.asyncAssertSuccess(s -> {
            consumerCreation.complete();
        }));
        consumerCreation.await(500);

        // WHEN an event is received
        ProtonDelivery delivery = mock(ProtonDelivery.class);
        Message msg = mock(Message.class);
        ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        verify(receiver).handler(messageHandler.capture());

        // THEN the message is released and settled
        messageHandler.getValue().handle(delivery, msg);
        verify(delivery).disposition(any(Released.class), eq(Boolean.TRUE));
    }

}
