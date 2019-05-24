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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;

/**
 * Verifies behavior of {@link GatewayMappingCommandHandler}.
 */
public class GatewayMappingCommandHandlerTest {

    private RegistrationClient regClient;
    private String tenantId;
    private String deviceId;
    private Handler<CommandContext> nextCommandHandler;
    private GatewayMappingCommandHandler gatewayMappingCommandHandler;
    private CommandContext commandContext;
    private Message commandMessage;

    /**
     * Sets up common fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        tenantId = "testTenant";
        deviceId = "testDevice";
        regClient = mock(RegistrationClient.class);
        final RegistrationClientFactory registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));
        final GatewayMapperImpl gatewayMapper = new GatewayMapperImpl(registrationClientFactory);

        nextCommandHandler = mock(Handler.class);
        gatewayMappingCommandHandler = new GatewayMappingCommandHandler(gatewayMapper, nextCommandHandler);

        commandMessage = mock(Message.class);
        when(commandMessage.getSubject()).thenReturn("testSubject");
        when(commandMessage.getCorrelationId()).thenReturn("testCorrelationId");
        final Command command = Command.from(commandMessage, tenantId, deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final Span currentSpan = mock(Span.class);
        commandContext = spy(CommandContext.from(command, delivery, receiver, currentSpan));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which 'via'
     * is not set.
     */
    @Test
    public void testHandleUsingEmptyDeviceData() {
        // GIVEN deviceData with no 'via'
        final JsonObject deviceData = new JsonObject();
        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with the original commandContext
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue(), is(commandContext));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which 'via'
     * and 'last-via' is set.
     */
    @Test
    public void testHandleUsingDeviceDataWithLastVia() {
        final String deviceViaId = "testDeviceVia";

        // GIVEN deviceData with 'via' and 'last-via'
        final JsonObject deviceData = new JsonObject();
        deviceData.put(RegistrationConstants.FIELD_VIA, deviceViaId);
        final JsonObject lastViaObject = new JsonObject();
        lastViaObject.put(Constants.JSON_FIELD_DEVICE_ID, deviceViaId);
        deviceData.put(RegistrationConstants.FIELD_LAST_VIA, lastViaObject);
        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with an adapted commandContext (with deviceViaId)
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getDeviceId(), is(deviceViaId));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which 'via'
     * and 'last-via' is set and where the command has a 'reply-to' value set.
     */
    @Test
    public void testHandleUsingDeviceDataWithLastViaAndCommandWithReplyTo() {
        final String deviceViaId = "testDeviceVia";
        final String replyToId = "the-reply-to-id";
        final String replyTo = String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, replyToId);

        // GIVEN deviceData with 'via' and 'last-via'
        final JsonObject deviceData = new JsonObject();
        deviceData.put(RegistrationConstants.FIELD_VIA, deviceViaId);
        final JsonObject lastViaObject = new JsonObject();
        lastViaObject.put(Constants.JSON_FIELD_DEVICE_ID, deviceViaId);
        deviceData.put(RegistrationConstants.FIELD_LAST_VIA, lastViaObject);
        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // AND a commandMessage with reply-to set
        when(commandMessage.getReplyTo()).thenReturn(replyTo);
        final Command command = Command.from(commandMessage, tenantId, deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final Span currentSpan = mock(Span.class);
        commandContext = spy(CommandContext.from(command, delivery, receiver, currentSpan));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with an adapted commandContext (with deviceViaId) and original reply-to id
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getDeviceId(), is(deviceViaId));
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getCommandMessage().getReplyTo(), is(replyTo));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which 'via'
     * is set but 'last-via' is not set.
     */
    @Test
    public void testHandleUsingDeviceDataWithoutLastVia() {
        final String deviceViaId = "testDeviceVia";

        // GIVEN deviceData with 'via' but no 'last-via'
        final JsonObject deviceData = new JsonObject();
        deviceData.put(RegistrationConstants.FIELD_VIA, deviceViaId);
        when(regClient.get(anyString(), any())).thenReturn(Future.succeededFuture(deviceData));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is not invoked and the commandContext is released
        verifyZeroInteractions(nextCommandHandler);
        verify(commandContext).release();
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when there is an exception retrieving device data.
     */
    @Test
    public void testHandleWithRegistrationClientException() {
        // GIVEN a registrationClient that returns a failed future
        when(regClient.get(anyString(), any())).thenReturn(Future.failedFuture("expected exception"));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is not invoked and the commandContext is released
        verifyZeroInteractions(nextCommandHandler);
        verify(commandContext).release();
    }
}
