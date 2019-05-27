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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.BiConsumer;

import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.Source;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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
    private HonoConnection connection;

    /**
     * Initializes fixture.
     */
    @Before
    public void setUp() {
        vertx = mock(Vertx.class);
        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
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
    @Test
    public void testCreateRegistersBiConsumerAsMessageHandler(final TestContext ctx) {

        // GIVEN an event consumer that releases all messages
        final BiConsumer<ProtonDelivery, Message> eventConsumer = (delivery, message) -> {
            ProtonHelper.released(delivery, true);
        };
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn("event/tenant");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);
        when(receiver.getRemoteQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);

        when(connection.createReceiver(
                anyString(),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyHandler())).thenReturn(Future.succeededFuture(receiver));

        final Async consumerCreation = ctx.async();
        EventConsumerImpl.create(
                connection,
                "tenant",
                eventConsumer,
                remoteDetach -> {}).setHandler(ctx.asyncAssertSuccess(rec -> consumerCreation.complete()));

        final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        verify(connection).createReceiver(eq("event/tenant"), eq(ProtonQoS.AT_LEAST_ONCE),
                messageHandler.capture(), anyHandler());
        consumerCreation.await();

        // WHEN an event is received
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final Message msg = mock(Message.class);
        messageHandler.getValue().handle(delivery, msg);

        // THEN the message is released and settled
        verify(delivery).disposition(any(Released.class), eq(Boolean.TRUE));
    }
}
