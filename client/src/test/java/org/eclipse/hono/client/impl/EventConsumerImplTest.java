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

import static org.mockito.Mockito.*;

import java.util.function.BiConsumer;

import io.vertx.ext.unit.junit.Timeout;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
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

    /**
     * Timeout each test after 5 secs.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCreateRegistersBiConsumerAsMessageHandler(final TestContext ctx) {

        // GIVEN an event consumer that releases all messages
        final Async consumerCreation = ctx.async();

        final BiConsumer<ProtonDelivery, Message> eventConsumer = (delivery, message) -> {
            ProtonHelper.released(delivery, true);
        };
        final RecordImpl attachments = new RecordImpl();
        final Source source = mock(Source.class);
        when(source.toString()).thenReturn("event/tenant");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.attachments()).thenReturn(attachments);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        when(receiver.open()).then(answer -> {
            consumerCreation.complete();
            return receiver;
        });
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(receiver);
        when(receiver.openHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler handler = invocation.getArgument(0);
            handler.handle(Future.succeededFuture(receiver));
            return receiver;
        });
        final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        EventConsumerImpl.create(
                vertx.getOrCreateContext(),
                new ClientConfigProperties(),
                con,
                "tenant",
                eventConsumer, open -> {},
                remoteDetach -> {});
        consumerCreation.await();
        verify(receiver).handler(messageHandler.capture());

        // WHEN an event is received
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final Message msg = mock(Message.class);
        messageHandler.getValue().handle(delivery, msg);

        // THEN the message is released and settled
        verify(delivery).disposition(any(Released.class), eq(Boolean.TRUE));
    }

    /**
     * Verifies that the close handler on a consumer calls the registered close hook.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testCloseHandlerCallsCloseHook(final TestContext ctx) {
        testHandlerCallsCloseHook(ctx, (receiver, captor) -> verify(receiver).closeHandler(captor.capture()));
    }

    /**
     * Verifies that the detach handler on a consumer calls the registered close hook.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDetachHandlerCallsCloseHook(final TestContext ctx) {
        testHandlerCallsCloseHook(ctx, (receiver, captor) -> verify(receiver).detachHandler(captor.capture()));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void testHandlerCallsCloseHook(final TestContext ctx, final BiConsumer<ProtonReceiver, ArgumentCaptor<Handler>> handlerCaptor) {

        // GIVEN an open event consumer
        final Async consumerCreation = ctx.async();
        final BiConsumer<ProtonDelivery, Message> eventConsumer = mock(BiConsumer.class);
        final RecordImpl attachments = new RecordImpl();
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn("source/address");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.attachments()).thenReturn(attachments);
        when(receiver.open()).then(answer -> {
            attachments.set(EventConsumerImpl.KEY_LINK_ESTABLISHED, Boolean.class, Boolean.TRUE);
            consumerCreation.complete();
            return receiver;
        });

        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(receiver);

        final Handler<String> closeHook = mock(Handler.class);
        final ArgumentCaptor<Handler> captor = ArgumentCaptor.forClass(Handler.class);
        EventConsumerImpl.create(vertx.getOrCreateContext(), new ClientConfigProperties(), con, "source/address", eventConsumer,
                ok->{}, closeHook );
        consumerCreation.await();
        handlerCaptor.accept(receiver, captor);

        // WHEN the receiver link is closed
        captor.getValue().handle(Future.succeededFuture(receiver));

        // THEN the close hook is called
        verify(closeHook).handle(any());

        // and the receiver link is closed
        verify(receiver).close();
    }
}
