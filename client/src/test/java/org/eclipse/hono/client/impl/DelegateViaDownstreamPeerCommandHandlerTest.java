/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of {@link DelegateViaDownstreamPeerCommandHandler}.
 */
public class DelegateViaDownstreamPeerCommandHandlerTest {

    private CommandContext commandContext;
    private ProtonDelivery commandDelivery;
    private DelegateViaDownstreamPeerCommandHandler delegateViaDownstreamPeerCommandHandler;
    private DelegatedCommandSender delegatedCommandSender;
    private String replyTo;
    private HonoConnection connection;
    private ProtonSender protonSender;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {
        final String tenantId = "testTenant";
        final String deviceId = "testDevice";
        replyTo = String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, "the-reply-to-id");

        final Message commandMessage = mock(Message.class);
        when(commandMessage.getSubject()).thenReturn("testSubject");
        when(commandMessage.getCorrelationId()).thenReturn("testCorrelationId");
        when(commandMessage.getReplyTo()).thenReturn(replyTo);
        final Command command = Command.from(commandMessage, tenantId, deviceId);
        commandDelivery = mock(ProtonDelivery.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final Span currentSpan = mock(Span.class);
        commandContext = spy(CommandContext.from(command, commandDelivery, receiver, currentSpan));

        delegateViaDownstreamPeerCommandHandler = new DelegateViaDownstreamPeerCommandHandler(
                (tenantIdParam, deviceIdParam) -> Future.succeededFuture(delegatedCommandSender));

        connection= mock(HonoConnection.class);
        when(connection.getConfig()).thenReturn(new ClientConfigProperties());
        final Vertx vertx = mock(Vertx.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(vertx.setTimer(anyLong(), anyHandler())).thenReturn(1L);
        protonSender = HonoClientUnitTestHelper.mockProtonSender();
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when sending the command message returns an
     * <em>Accepted</em> delivery result.
     */
    @Test
    public void testHandleWithAcceptedDeliveryResult() {

        // GIVEN a message sender that returns an 'Accepted' delivery result
        final ProtonDelivery protonDelivery = mock(ProtonDelivery.class);
        when(protonDelivery.getRemoteState()).thenReturn(Accepted.getInstance());
        // not using a DelegatedCommandSender mock here since mocking the #sendAndWaitForOutcome(Message, SpanContext) method (which has a default implementation) doesn't seem to work
        delegatedCommandSender = new DelegatedCommandSenderImpl(connection, protonSender) {
            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext parent) {
                assertThat(message.getReplyTo(), is(replyTo));
                return Future.succeededFuture(protonDelivery);
            }
        };

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'accepted' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue(), is(instanceOf(Accepted.class)));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when sending the command message returns a
     * <em>Rejected</em> delivery result.
     */
    @Test
    public void testHandleWithRejectedDeliveryResult() {

        // GIVEN a message sender that returns a 'Rejected' delivery result
        final ProtonDelivery protonDelivery = mock(ProtonDelivery.class);
        final Rejected rejected = new Rejected();
        final ErrorCondition error = new ErrorCondition(Symbol.valueOf("someError"), "error message");
        rejected.setError(error);
        when(protonDelivery.getRemoteState()).thenReturn(rejected);
        delegatedCommandSender = new DelegatedCommandSenderImpl(connection, protonSender) {
            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext parent) {
                assertThat(message.getReplyTo(), is(replyTo));
                return Future.succeededFuture(protonDelivery);
            }
        };

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'rejected' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue(), is(instanceOf(Rejected.class)));
        assertThat(((Rejected) deliveryStateArgumentCaptor.getValue()).getError(), is(error));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when sending the command message returns a
     * <em>Modified</em> delivery result.
     */
    @Test
    public void testHandleWithModifiedDeliveryResult() {

        // GIVEN a message sender that returns a 'Modified' delivery result
        final ProtonDelivery protonDelivery = mock(ProtonDelivery.class);
        final Modified modified = new Modified();
        modified.setDeliveryFailed(true);
        modified.setUndeliverableHere(true);
        when(protonDelivery.getRemoteState()).thenReturn(modified);
        delegatedCommandSender = new DelegatedCommandSenderImpl(connection, protonSender) {
            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext parent) {
                assertThat(message.getReplyTo(), is(replyTo));
                return Future.succeededFuture(protonDelivery);
            }
        };

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'modified' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue(), is(instanceOf(Modified.class)));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when sending the command message returns a
     * <em>Released</em> delivery result.
     */
    @Test
    public void testHandleWithReleasedDeliveryResult() {

        // GIVEN a message sender that returns an 'Released' delivery result
        final ProtonDelivery protonDelivery = mock(ProtonDelivery.class);
        when(protonDelivery.getRemoteState()).thenReturn(Released.getInstance());
        delegatedCommandSender = new DelegatedCommandSenderImpl(connection, protonSender) {
            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext parent) {
                assertThat(message.getReplyTo(), is(replyTo));
                return Future.succeededFuture(protonDelivery);
            }
        };

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'released' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue(), is(instanceOf(Released.class)));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when sending the command message fails.
     */
    @Test
    public void testHandleWithFailureToSend() {

        // GIVEN a message sender that fails to send the message
        delegatedCommandSender = new DelegatedCommandSenderImpl(connection, protonSender) {
            @Override
            public Future<ProtonDelivery> sendAndWaitForOutcome(final Message message, final SpanContext parent) {
                return Future.failedFuture("expected send failure");
            }
        };

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'released' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue(), is(instanceOf(Released.class)));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when creating the message sender fails.
     */
    @Test
    public void testHandleWithFailureToCreateSender() {

        // GIVEN a scenario where sender creation fails
        delegateViaDownstreamPeerCommandHandler = new DelegateViaDownstreamPeerCommandHandler(
                (tenantIdParam, deviceIdParam) -> Future.failedFuture("expected sender creation failure"));

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'released' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue(), is(instanceOf(Released.class)));
    }
}
