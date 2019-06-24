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

import static org.eclipse.hono.client.impl.VertxMockSupport.mockHandler;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.util.Collections;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonReceiver;

/**
 * Verifies behavior of {@link GatewayMappingCommandHandler}.
 */
public class GatewayMappingCommandHandlerTest {

    private RegistrationClient regClient;
    private DeviceConnectionClient devConClient;
    private String tenantId;
    private String deviceId;
    private Handler<CommandContext> nextCommandHandler;
    private GatewayMappingCommandHandler gatewayMappingCommandHandler;
    private CommandContext commandContext;
    private Message commandMessage;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {
        final SpanContext spanContext = mock(SpanContext.class);
        final Span span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        final Tracer.SpanBuilder spanBuilder = HonoClientUnitTestHelper.mockSpanBuilder(span);
        final Tracer tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        tenantId = "testTenant";
        deviceId = "testDevice";
        regClient = mock(RegistrationClient.class);
        final RegistrationClientFactory registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));

        devConClient = mock(DeviceConnectionClient.class);
        final DeviceConnectionClientFactory deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(anyString()))
                .thenReturn(Future.succeededFuture(devConClient));
        final GatewayMapperImpl gatewayMapper = new GatewayMapperImpl(registrationClientFactory, deviceConnectionClientFactory, tracer);

        nextCommandHandler = mockHandler();
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
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which no
     * 'via' entry is set.
     */
    @Test
    public void testHandleUsingEmptyDeviceData() {
        // GIVEN assertRegistrationResult with no 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with the original commandContext
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue(), is(commandContext));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which a
     * 'via' entry is set and for which the last known gateway is set.
     */
    @Test
    public void testHandleReturningLastUsedGateway() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistration result with non-empty 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a non-empty getLastKnownGatewayForDevice result
        final JsonObject lastKnownGatewayResult = new JsonObject();
        lastKnownGatewayResult.put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any())).thenReturn(Future.succeededFuture(lastKnownGatewayResult));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with an adapted commandContext (with gatewayId)
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getDeviceId(), is(gatewayId));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which a
     * 'via' entry is set and for which the last known gateway is set and where the command has a 'reply-to' value set.
     */
    @Test
    public void testHandleReturningLastUsedGatewayWithCommandReplyToSet() {
        final String gatewayId = "testDeviceVia";
        final String replyToId = "the-reply-to-id";
        final String replyTo = String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, replyToId);

        // GIVEN assertRegistration result with non-empty 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a non-empty getLastKnownGatewayForDevice result
        final JsonObject lastKnownGatewayResult = new JsonObject();
        lastKnownGatewayResult.put(DeviceConnectionConstants.FIELD_GATEWAY_ID, gatewayId);
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any())).thenReturn(Future.succeededFuture(lastKnownGatewayResult));

        // AND a commandMessage with reply-to set
        when(commandMessage.getReplyTo()).thenReturn(replyTo);
        final Command command = Command.from(commandMessage, tenantId, deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        final Span currentSpan = mock(Span.class);
        commandContext = spy(CommandContext.from(command, delivery, receiver, currentSpan));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with an adapted commandContext (with gatewayId) and original reply-to id
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getDeviceId(), is(gatewayId));
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getCommandMessage().getReplyTo(), is(replyTo));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when called in the context of a device for which the
     * 'via' entry contains one gateway and for which no last known gateway is set.
     */
    @Test
    public void testHandleReturningSingleViaGateway() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistrationResult with a single 'via' array entry
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray(Collections.singletonList(gatewayId));
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a 404 getLastKnownGatewayForDevice response
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is called with an adapted commandContext (with gatewayId)
        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(nextCommandHandler).handle(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getDeviceId(), is(gatewayId));
    }

    /**
     * Verifies that the <em>handle</em> method behaves correctly when no mapped gateway could be determined.
     */
    @Test
    public void testHandleWithNoMappedGatewayFound() {
        final String gatewayId = "testDeviceVia";

        // GIVEN assertRegistrationResult with 'via'
        final JsonObject assertRegistrationResult = new JsonObject();
        final JsonArray viaArray = new JsonArray();
        viaArray.add(gatewayId);
        viaArray.add("otherGatewayId");
        assertRegistrationResult.put(RegistrationConstants.FIELD_VIA, viaArray);
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(assertRegistrationResult));
        // and a 404 getLastKnownGatewayForDevice response
        when(devConClient.getLastKnownGatewayForDevice(anyString(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

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
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.failedFuture("expected exception"));

        // WHEN handle() is invoked
        gatewayMappingCommandHandler.handle(commandContext);

        // THEN the nextCommandHandler is not invoked and the commandContext is released
        verifyZeroInteractions(nextCommandHandler);
        verify(commandContext).release();
    }
}
