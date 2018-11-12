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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@code AbstractSender}.
 *
 */
public class AbstractSenderTest {

    private ProtonSender protonSender;
    private ClientConfigProperties config;
    private Context context;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        protonSender = HonoClientUnitTestHelper.mockProtonSender();
        config = new ClientConfigProperties();
        context = HonoClientUnitTestHelper.mockContext(mock(Vertx.class));
    }

    /**
     * Verifies that registration assertions are not required if
     * the peer does not support validation of assertions.
     */
    @Test
    public void testIsRegistrationAssertionRequiredReturnsFalse() {

        when(protonSender.getRemoteOfferedCapabilities()).thenReturn(null);
        final AbstractSender sender = newSender("tenant", "endpoint");
        assertFalse(sender.isRegistrationAssertionRequired());
    }

    /**
     * Verifies that registration assertions are required if
     * the peer supports validation of assertions.
     */
    @Test
    public void testIsRegistrationAssertionRequiredReturnsTrue() {

        when(protonSender.getRemoteOfferedCapabilities())
            .thenReturn(new Symbol[] { Constants.CAP_REG_ASSERTION_VALIDATION });
        final AbstractSender sender = newSender("tenant", "endpoint");
        assertTrue(sender.isRegistrationAssertionRequired());
    }

    /**
     * Verifies that the sender removes the registration assertion
     * from a message if the peer does not support validation of
     * assertions.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSendMessageRemovesRegistrationAssertion() {

        // GIVEN a sender that is connected to a peer which does not
        // support validation of registration assertions
        when(protonSender.getRemoteOfferedCapabilities()).thenReturn(null);
        when(protonSender.send(any(Message.class), any(Handler.class))).thenReturn(mock(ProtonDelivery.class));
        final AbstractSender sender = newSender("tenant", "endpoint");

        // WHEN sending a message
        sender.send("device", "some payload", "application/text", "token");

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
        final Future<ProtonDelivery> result = sender.send("device", "some payload", "application/text", "token");

        // THEN the message is not sent
        assertFalse(result.succeeded());
        verify(protonSender, never()).send(any(Message.class));
    }

    /**
     * Verifies credits available.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCredits() {
        final AbstractSender sender = newSender("tenant", "endpoint");
        when(protonSender.getCredit()).thenReturn(10);
        assertThat(sender.getCredit(), is(10));
        when(protonSender.getCredit()).thenReturn(0);
        assertThat(sender.getCredit(), is(0));
    }

    private AbstractSender newSender(final String tenantId, final String targetAddress) {

        return new AbstractSender(
                config,
                protonSender,
                tenantId,
                targetAddress,
                context,
                null) {

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
                return mock(Span.class);
            }
        };
    }
}
