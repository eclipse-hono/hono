/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.application.client.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link ProtonBasedCommandSender}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class ProtonBasedCommandSenderTest {

    private ProtonBasedCommandSender commandSender;
    private ProtonSender sender;
    private ProtonDelivery protonDelivery;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        final var vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
        final HonoConnection connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx);

        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(
                anyString(),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(receiver));

        protonDelivery = mock(ProtonDelivery.class);
        when(protonDelivery.remotelySettled()).thenReturn(true);
        when(protonDelivery.getRemoteState()).thenReturn(new Accepted());

        sender = AmqpClientUnitTestHelper.mockProtonSender();
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(protonDelivery);
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));

        commandSender = new ProtonBasedCommandSender(connection, SendMessageSampler.Factory.noop());
    }

    /**
     * Verifies that a one-way command sent to a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendOneWayCommandSucceeds(final VertxTestContext ctx) {
        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String subject = "setVolume";
        final ArgumentCaptor<Handler<ProtonDelivery>> dispositionHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // WHEN sending a one-way command with some application properties and payload
        final Future<Void> sendCommandFuture = commandSender
                .sendOneWayCommand(tenantId, deviceId, subject, Buffer.buffer("{\"value\": 20}"), null,
                        NoopSpan.INSTANCE.context())
                .onComplete(ctx.succeedingThenComplete());

        // VERIFY that the command is being sent
        verify(sender).send(messageCaptor.capture(), dispositionHandlerCaptor.capture());

        // VERIFY that the future waits for the disposition to be updated by the peer
        assertThat(sendCommandFuture.isComplete()).isFalse();

        // THEN the disposition is updated and the peer accepts the message
        dispositionHandlerCaptor.getValue().handle(protonDelivery);

        //VERIFY if the message properties are properly set
        final Message message = messageCaptor.getValue();
        assertThat(message.getSubject()).isEqualTo(subject);
        assertThat(sendCommandFuture.isComplete()).isTrue();
    }

    /**
     * Verifies that a command asynchronously sent to a device succeeds.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendAsyncCommandSucceeds(final VertxTestContext ctx) {
        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String subject = "setVolume";
        final String correlationId = UUID.randomUUID().toString();
        final String replyId = "reply-id";
        final ArgumentCaptor<Handler<ProtonDelivery>> dispositionHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // WHEN sending a one-way command with some application properties and payload
        final Future<Void> sendCommandFuture = commandSender
                .sendAsyncCommand(tenantId, deviceId, subject, correlationId, replyId, Buffer.buffer("{\"value\": 20}"),
                        null, NoopSpan.INSTANCE.context())
                .onComplete(ctx.succeedingThenComplete());

        // VERIFY that the command is being sent
        verify(sender).send(messageCaptor.capture(), dispositionHandlerCaptor.capture());

        // VERIFY that the future waits for the disposition to be updated by the peer
        assertThat(sendCommandFuture.isComplete()).isFalse();

        // THEN the disposition is updated and the peer accepts the message
        dispositionHandlerCaptor.getValue().handle(protonDelivery);

        //VERIFY if the message properties are properly set
        final Message message = messageCaptor.getValue();
        assertThat(message.getSubject()).isEqualTo(subject);
        assertThat(message.getCorrelationId()).isEqualTo(correlationId);
        assertThat(message.getReplyTo()).isEqualTo(String.format("%s/%s/%s",
                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, replyId));
        assertThat(sendCommandFuture.isComplete()).isTrue();
    }
}
