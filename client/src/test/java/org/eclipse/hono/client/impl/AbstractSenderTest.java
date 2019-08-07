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
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@code AbstractSender}.
 *
 */
public class AbstractSenderTest {

    private ProtonSender protonSender;
    private Vertx vertx;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        protonSender = HonoClientUnitTestHelper.mockProtonSender();
        vertx = mock(Vertx.class);
    }

    /**
     * Verifies that the sender removes the registration assertion
     * from a message if the peer does not support validation of
     * assertions.
     */
    @Test
    public void testSendMessageRemovesRegistrationAssertion() {

        // GIVEN a sender that is connected to a peer which does not
        // support validation of registration assertions
        when(protonSender.getRemoteOfferedCapabilities()).thenReturn(null);
        when(protonSender.send(any(Message.class), anyHandler())).thenReturn(mock(ProtonDelivery.class));
        final AbstractSender sender = newSender("tenant", "endpoint");

        // WHEN sending a message
        final Message msg = ProtonHelper.message("some payload");
        msg.setContentType("application/text");
        MessageHelper.addDeviceId(msg, "device");
        sender.send(msg);

        // THEN the message is sent without the registration assertion
        final ArgumentCaptor<Message> sentMessage = ArgumentCaptor.forClass(Message.class);
        verify(protonSender).send(sentMessage.capture());
        assertNull(MessageHelper.getAndRemoveRegistrationAssertion(sentMessage.getValue()));
    }

    /**
     * Verifies that the sender fails if no credit is available.
     */
    @Test
    public void testSendMessageFailsOnLackOfCredit() {

        // GIVEN a sender that has no credit
        when(protonSender.sendQueueFull()).thenReturn(Boolean.TRUE);
        final AbstractSender sender = newSender("tenant", "endpoint");

        // WHEN trying to send a message
        final Message msg = ProtonHelper.message("test");
        final Future<ProtonDelivery> result = sender.send(msg);

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(protonSender, never()).send(any(Message.class));
    }

    /**
     * Verifies that the sender fails if a timeout occurs.
     */
    @Test
    public void testSendMessageFailsOnTimeout() {

        // GIVEN a sender that won't receive a delivery update on sending a message 
        // and directly triggers the timeout handler
        when(protonSender.send(any(Message.class), anyHandler())).thenReturn(mock(ProtonDelivery.class));
        when(vertx.setTimer(anyLong(), anyHandler())).thenAnswer(invocation -> {
            final Handler<Long> handler = invocation.getArgument(1);
            final long timerId = 1;
            handler.handle(timerId);
            return timerId;
        });
        final AbstractSender sender = newSender("tenant", "endpoint");

        // WHEN sending a message
        final Message message = mock(Message.class);
        final Span span = mock(Span.class);
        final Future<ProtonDelivery> result = sender.sendMessageAndWaitForOutcome(message, span);

        // THEN the returned result future will fail with a 503 status code.
        assertFalse(result.succeeded());
        assertTrue(result.cause() instanceof ServerErrorException);
        assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServerErrorException) result.cause()).getErrorCode());
        verify(span).finish();
    }

    /**
     * Verifies credits available.
     *
     */
    @Test
    public void testCredits() {
        final AbstractSender sender = newSender("tenant", "endpoint");
        when(protonSender.getCredit()).thenReturn(10);
        assertThat(sender.getCredit(), is(10));
        when(protonSender.getCredit()).thenReturn(0);
        assertThat(sender.getCredit(), is(0));
    }

    private AbstractSender newSender(final String tenantId, final String targetAddress) {

        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        return new AbstractSender(
                connection,
                protonSender,
                tenantId,
                targetAddress) {

            @Override
            public String getEndpoint() {
                return null;
            }

            @Override
            protected Future<ProtonDelivery> sendMessage(final Message message, final Span currentSpan) {
                protonSender.send(message);
                return Future.succeededFuture(mock(ProtonDelivery.class));
            }

            /**
             * @return an incomplete future.
             */
            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message) {
                protonSender.send(message);
                return Future.future();
            }

            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext context) {
                protonSender.send(message);
                return null;
            }

            @Override
            protected String getTo(final String deviceId) {
                return null;
            }

            @Override
            protected Span startSpan(final SpanContext context, final Message rawMessage) {
                final SpanContext spanContext = mock(SpanContext.class);
                final Span span = mock(Span.class);
                when(span.context()).thenReturn(spanContext);
                return span;
            }
        };
    }
}
