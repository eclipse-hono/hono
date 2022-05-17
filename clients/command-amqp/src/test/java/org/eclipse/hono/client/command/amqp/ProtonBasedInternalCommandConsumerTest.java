/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.client.command.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link ProtonBasedInternalCommandConsumer}.
 *
 */
public class ProtonBasedInternalCommandConsumerTest {


    private ProtonBasedInternalCommandConsumer internalCommandConsumer;
    private CommandHandlers commandHandlers;
    private Context context;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final HonoConnection honoConnection = AmqpClientUnitTestHelper.mockHonoConnection(mock(Vertx.class));

        final String adapterInstanceId = "adapterInstanceId";
        commandHandlers = new CommandHandlers();
        internalCommandConsumer = new ProtonBasedInternalCommandConsumer(honoConnection, adapterInstanceId,
                commandHandlers);
        context = VertxMockSupport.mockContext(mock(Vertx.class));
    }

    /**
     * Verifies that the consumer handles an invalid message with a missing address by updating the delivery
     * with status <em>rejected</em>.
     */
    @Test
    void testHandleCommandMessageWithInvalidMessageAddress() {
        final Message msg = mock(Message.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        internalCommandConsumer.handleCommandMessage(delivery, msg);

        final ArgumentCaptor<DeliveryState> deliveryStateCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryStateCaptor.capture(), anyBoolean());
        assertThat(deliveryStateCaptor.getValue()).isNotNull();
        assertThat(deliveryStateCaptor.getValue()).isInstanceOf(Rejected.class);
    }

    /**
     * Verifies that the consumer handles a valid message for which no handler is found by updating the delivery
     * with status <em>released</em>.
     */
    @Test
    void testHandleCommandMessageWithNoHandlerFound() {
        final Message msg = mock(Message.class);
        final String deviceId = "4711";
        when(msg.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        internalCommandConsumer.handleCommandMessage(delivery, msg);

        final ArgumentCaptor<DeliveryState> deliveryStateCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryStateCaptor.capture(), anyBoolean());
        assertThat(deliveryStateCaptor.getValue()).isNotNull();
        assertThat(deliveryStateCaptor.getValue()).isInstanceOf(Released.class);
    }

    /**
     * Verifies that the consumer handles a valid message by invoking the matching command handler.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testHandleCommandMessageWithHandlerForDevice() {
        final String deviceId = "4711";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(mock(ProtonDelivery.class), message);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    /**
     * Verifies that the consumer handles a valid message, targeted at a gateway, by invoking the matching command
     * handler.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testHandleCommandMessageWithHandlerForGateway() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);
        message.setApplicationProperties(
                new ApplicationProperties(Collections.singletonMap(MessageHelper.APP_PROPERTY_CMD_VIA, gatewayId)));

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(Constants.DEFAULT_TENANT, gatewayId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(mock(ProtonDelivery.class), message);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    /**
     * Verifies that the consumer handles a valid message, for which the matching command handler is associated
     * with a gateway, by invoking the handler and adopting the gateway identifier in the command object.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testHandleCommandMessageWithHandlerForGatewayAndSpecificDevice() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(Constants.DEFAULT_TENANT, deviceId, gatewayId, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(mock(ProtonDelivery.class), message);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

}
