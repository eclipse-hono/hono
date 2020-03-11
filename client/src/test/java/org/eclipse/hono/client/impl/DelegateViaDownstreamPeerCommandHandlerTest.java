/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link DelegateViaDownstreamPeerCommandHandler}.
 */
public class DelegateViaDownstreamPeerCommandHandlerTest {

    private CommandContext commandContext;
    private ProtonDelivery commandDelivery;
    private DelegateViaDownstreamPeerCommandHandler delegateViaDownstreamPeerCommandHandler;
    private DelegatedCommandSender delegatedCommandSender;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {
        final String tenantId = "testTenant";
        final String deviceId = "testDevice";
        final String replyTo = String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, "the-reply-to-id");

        final Message commandMessage = mock(Message.class);
        when(commandMessage.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId));
        when(commandMessage.getSubject()).thenReturn("testSubject");
        when(commandMessage.getCorrelationId()).thenReturn("testCorrelationId");
        when(commandMessage.getReplyTo()).thenReturn(replyTo);
        final Command command = Command.from(commandMessage, tenantId, deviceId);
        commandDelivery = mock(ProtonDelivery.class);
        final Span currentSpan = mock(Span.class);
        commandContext = spy(CommandContext.from(command, commandDelivery, currentSpan));

        delegateViaDownstreamPeerCommandHandler = new DelegateViaDownstreamPeerCommandHandler(
                (tenantIdParam, deviceIdParam) -> Future.succeededFuture(delegatedCommandSender));

        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getConfig()).thenReturn(new ClientConfigProperties());
        final Vertx vertx = mock(Vertx.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenReturn(1L);
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
        delegatedCommandSender = mock(DelegatedCommandSender.class);
        when(delegatedCommandSender.sendCommandMessage(any(), any())).thenReturn(Future.succeededFuture(protonDelivery));

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'accepted' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue()).isInstanceOf(Accepted.class);
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
        delegatedCommandSender = mock(DelegatedCommandSender.class);
        when(delegatedCommandSender.sendCommandMessage(any(), any())).thenReturn(Future.succeededFuture(protonDelivery));

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'rejected' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue()).isInstanceOf(Rejected.class);
        assertThat(((Rejected) deliveryStateArgumentCaptor.getValue()).getError()).isEqualTo(error);
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
        delegatedCommandSender = mock(DelegatedCommandSender.class);
        when(delegatedCommandSender.sendCommandMessage(any(), any())).thenReturn(Future.succeededFuture(protonDelivery));

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'modified' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue()).isInstanceOf(Modified.class);
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
        delegatedCommandSender = mock(DelegatedCommandSender.class);
        when(delegatedCommandSender.sendCommandMessage(any(), any())).thenReturn(Future.succeededFuture(protonDelivery));

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'released' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue()).isInstanceOf(Released.class);
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when sending the command message fails.
     */
    @Test
    public void testHandleWithFailureToSend() {

        // GIVEN a message sender that fails to send the message
        delegatedCommandSender = mock(DelegatedCommandSender.class);
        when(delegatedCommandSender.sendCommandMessage(any(), any())).thenReturn(Future.failedFuture("expected send failure"));

        // WHEN handle() is invoked
        delegateViaDownstreamPeerCommandHandler.handle(commandContext);

        // THEN the command context delivery is updated with the 'released' outcome
        final ArgumentCaptor<DeliveryState> deliveryStateArgumentCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(commandDelivery).disposition(deliveryStateArgumentCaptor.capture(), anyBoolean());
        assertThat(deliveryStateArgumentCaptor.getValue()).isInstanceOf(Released.class);
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
        assertThat(deliveryStateArgumentCaptor.getValue()).isInstanceOf(Released.class);
    }
}
