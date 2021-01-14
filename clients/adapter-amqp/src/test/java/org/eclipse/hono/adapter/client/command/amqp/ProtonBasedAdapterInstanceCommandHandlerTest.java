/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.CommandHandlers;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Tests verifying behavior of {@link ProtonBasedAdapterInstanceCommandHandler}.
 *
 */
public class ProtonBasedAdapterInstanceCommandHandlerTest {


    private ProtonBasedAdapterInstanceCommandHandler adapterInstanceCommandHandler;
    private CommandHandlers commandHandlers;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final String adapterInstanceId = "adapterInstanceId";
        adapterInstanceCommandHandler = new ProtonBasedAdapterInstanceCommandHandler(tracer, adapterInstanceId);
        commandHandlers = new CommandHandlers();
    }

    @Test
    void testHandleCommandMessageWithInvalidMessage() {
        final Message msg = mock(Message.class);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        adapterInstanceCommandHandler.handleCommandMessage(msg, delivery, commandHandlers);

        final ArgumentCaptor<DeliveryState> deliveryStateCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryStateCaptor.capture(), anyBoolean());
        assertThat(deliveryStateCaptor.getValue()).isNotNull();
        assertThat(deliveryStateCaptor.getValue()).isInstanceOf(Rejected.class);
    }

    @Test
    void testHandleCommandMessageWithNoHandlerFound() {
        final Message msg = mock(Message.class);
        final String deviceId = "4711";
        when(msg.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        adapterInstanceCommandHandler.handleCommandMessage(msg, delivery, commandHandlers);

        final ArgumentCaptor<DeliveryState> deliveryStateCaptor = ArgumentCaptor.forClass(DeliveryState.class);
        verify(delivery).disposition(deliveryStateCaptor.capture(), anyBoolean());
        assertThat(deliveryStateCaptor.getValue()).isNotNull();
        assertThat(deliveryStateCaptor.getValue()).isInstanceOf(Released.class);
    }

    @Test
    void testHandleCommandMessageWithHandlerForDevice() {
        final String deviceId = "4711";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, commandHandler);

        adapterInstanceCommandHandler.handleCommandMessage(message, mock(ProtonDelivery.class), commandHandlers);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    @Test
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

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(Constants.DEFAULT_TENANT, gatewayId, null, commandHandler);

        adapterInstanceCommandHandler.handleCommandMessage(message, mock(ProtonDelivery.class), commandHandlers);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    @Test
    void testHandleCommandMessageWithHandlerForGatewayAndSpecificDevice() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";
        final String correlationId = "the-correlation-id";
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId(correlationId);

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(Constants.DEFAULT_TENANT, deviceId, gatewayId, commandHandler);

        adapterInstanceCommandHandler.handleCommandMessage(message, mock(ProtonDelivery.class), commandHandlers);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

}
