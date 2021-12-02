/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of {@link MappingAndDelegatingCommandHandler}.
 */
public class MappingAndDelegatingCommandHandlerTest {

    private CommandTargetMapper commandTargetMapper;
    private AdapterInstanceCommandHandler adapterInstanceCommandHandler;
    private String adapterInstanceId;
    private MappingAndDelegatingCommandHandler mappingAndDelegatingCommandHandler;
    // sender used in the DelegatedCommandSender
    private ProtonSender sender;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final Vertx vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(1);
            handler.handle(null);
            return null;
        }).when(vertx).setTimer(anyLong(), VertxMockSupport.anyHandler());
        final EventBus eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        adapterInstanceId = UUID.randomUUID().toString();

        final ClientConfigProperties props = new ClientConfigProperties();
        props.setSendMessageTimeout(0);
        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, props);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        sender = HonoClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));

        adapterInstanceCommandHandler = new AdapterInstanceCommandHandler(tracer, adapterInstanceId);
        commandTargetMapper = mock(CommandTargetMapper.class);

        mappingAndDelegatingCommandHandler = new MappingAndDelegatingCommandHandler(
                connection, commandTargetMapper, adapterInstanceCommandHandler, adapterInstanceId, SendMessageSampler.noop());
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the given command message shall get mapped to a local command handler.
     */
    @Test
    public void testMapWithLocalCommandHandler() {
        final String deviceId = "4711";

        // GIVEN a registered commandHandler for the deviceId
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, localHandlerCmdContextRef::set);

        // AND the deviceId commandHandler registered for the local adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the local command handler is invoked with the unchanged command message
        assertThat(localHandlerCmdContextRef.get()).isNotNull();
        assertThat(localHandlerCmdContextRef.get().getCommand()).isNotNull();
        assertThat(localHandlerCmdContextRef.get().getCommand().getCommandMessage()).isEqualTo(message);
        assertThat(localHandlerCmdContextRef.get().getCommand().isValid()).isTrue();
        // AND the command message delivery is unchanged (that would be done by the commandHandler)
        verify(delivery, never()).disposition(any(), anyBoolean());
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the given <em>invalid</em> command message shall get mapped to a local command handler.
     */
    @Test
    public void testMapWithLocalCommandHandlerAndInvalidMessage() {
        final String deviceId = "4711";

        // GIVEN a registered commandHandler for the deviceId
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, localHandlerCmdContextRef::set);

        // AND the deviceId commandHandler registered for the local adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        // WHEN mapping and delegating an invalid command message
        final Message message = getValidCommandMessage(deviceId);
        message.setSubject(null); // make the message invalid
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the local command handler is invoked with the unchanged command message
        assertThat(localHandlerCmdContextRef.get()).isNotNull();
        assertThat(localHandlerCmdContextRef.get().getCommand()).isNotNull();
        assertThat(localHandlerCmdContextRef.get().getCommand().getCommandMessage()).isEqualTo(message);
        assertThat(localHandlerCmdContextRef.get().getCommand().isValid()).isFalse();
        // AND the command message delivery is unchanged (that would be done by the commandHandler)
        verify(delivery, never()).disposition(any(), anyBoolean());
    }

    /**
     * Verifies that a command message with an address that doesn't contain a device ID
     * gets rejected.
     */
    @Test
    public void testMapForMessageHavingAddressWithoutDeviceId() {

        // GIVEN a command message with an address that does not
        // contain a device ID
        final String deviceId = "4711";
        final Message message = getValidCommandMessage(deviceId);
        message.setAddress(String.format("%s/%s", CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT));

        // WHEN mapping and delegating the command
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the disposition is REJECTED
        verify(delivery).disposition(
                argThat(state -> Constants.AMQP_BAD_REQUEST.equals(((Rejected) state).getError().getCondition())),
                eq(true));
        // and the message is not being delegated
        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that a command message with an address that contains a tenant which doesn't
     * match the scope of the command receiver link gets rejected.
     */
    @Test
    public void testMapForMessageHavingAddressWithInvalidTenant() {

        // GIVEN a command message with an address that contains an
        // invalid tenant
        final String deviceId = "4711";
        final Message message = getValidCommandMessage(deviceId);
        message.setAddress(String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, "wrong-tenant", deviceId));

        // WHEN mapping and delegating the command
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the disposition is REJECTED
        verify(delivery).disposition(
                argThat(state -> AmqpError.UNAUTHORIZED_ACCESS.equals(((Rejected) state).getError().getCondition())),
                eq(true));
        // and the message is not being delegated
        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * no command handling adapter instance is found for the command message device.
     */
    @Test
    public void testMapWithNoAdapterInstanceFound() {
        final String deviceId = "4711";

        // GIVEN no registered commandHandler for the deviceId
        // but a deviceId commandHandler registered for the local adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        // WHEN mapping and delegating a command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the disposition is RELEASED
        verify(delivery).disposition(any(Released.class), eq(true));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * no command handling adapter instance is found for the command message device and the command message
     * is invalid.
     */
    @Test
    public void testMapWithNoAdapterInstanceFoundAndMessageInvalid() {
        final String deviceId = "4711";

        // GIVEN no registered commandHandler for the deviceId
        // but a deviceId commandHandler registered for the local adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        // WHEN mapping and delegating an invalid command message
        final Message message = getValidCommandMessage(deviceId);
        message.setSubject(null); // make the message invalid
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the disposition is REJECTED
        verify(delivery).disposition(any(Rejected.class), eq(true));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command handler is not found.
     */
    @Test
    public void testMapWithCommandHandlerNotFound() {
        final String deviceId = "4711";

        // GIVEN a 'NOT_FOUND' error when looking up the adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        // WHEN mapping and delegating a command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the disposition is RELEASED
        verify(delivery).disposition(any(Released.class), eq(true));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the given command message shall get mapped to a local command handler, handling commands for a gateway.
     */
    @Test
    public void testMapWithLocalCommandHandlerForGateway() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";

        // GIVEN a registered commandHandler for the gateway
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, gatewayId, null, localHandlerCmdContextRef::set);

        // AND the gatewayId commandHandler registered for the local adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(gatewayId, adapterInstanceId)));

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the local command handler is invoked with the command message, its device id set to the gateway
        assertThat(localHandlerCmdContextRef.get()).isNotNull();
        final Command command = localHandlerCmdContextRef.get().getCommand();
        assertThat(command).isNotNull();
        assertThat(command.isValid()).isTrue();
        assertThat(command.getDeviceId()).isEqualTo(gatewayId);
        assertThat(command.getOriginalDeviceId()).isEqualTo(deviceId);
        // AND the command message delivery is unchanged (that would be done by the commandHandler)
        verify(delivery, never()).disposition(any(), anyBoolean());
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the given command message shall get mapped to a local command handler, handling commands for the specific
     * device for a gateway.
     */
    @Test
    public void testMapWithLocalCommandHandlerForGatewayAndSpecificDevice() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";

        // GIVEN a registered commandHandler for the deviceId and gatewayId
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, deviceId, gatewayId, localHandlerCmdContextRef::set);

        // AND the deviceId commandHandler registered for the local adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the local command handler is invoked with the command message, its device id set to the gateway
        assertThat(localHandlerCmdContextRef.get()).isNotNull();
        final Command command = localHandlerCmdContextRef.get().getCommand();
        assertThat(command).isNotNull();
        assertThat(command.isValid()).isTrue();
        assertThat(command.getDeviceId()).isEqualTo(gatewayId);
        assertThat(command.getOriginalDeviceId()).isEqualTo(deviceId);
        // AND the command message delivery is unchanged (that would be done by the commandHandler)
        verify(delivery, never()).disposition(any(), anyBoolean());
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by another adapter instance.
     */
    @Test
    public void testMapWithCommandHandlerOnAnotherInstance() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for another adapter instance (not the local one)
        final String otherAdapterInstance = "otherAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, otherAdapterInstance)));

        // AND an ACCEPTED result when sending the command message to another adapter instance
        final ProtonDelivery sendMsgDeliveryUpdate = mock(ProtonDelivery.class);
        when(sendMsgDeliveryUpdate.getRemoteState()).thenReturn(new Accepted());
        when(sendMsgDeliveryUpdate.remotelySettled()).thenReturn(true);
        final AtomicReference<Message> delegatedMessageRef = new AtomicReference<>();
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            delegatedMessageRef.set(invocation.getArgument(0));
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(sendMsgDeliveryUpdate);
            return mock(ProtonDelivery.class);
        });

        // register local command handler - that shall not get used
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, localHandlerCmdContextRef::set);

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the delivery gets ACCEPTED as well
        verify(delivery).disposition(any(Accepted.class), eq(true));
        assertThat(localHandlerCmdContextRef.get()).isNull();
        final Message delegatedMessage = delegatedMessageRef.get();
        assertThat(delegatedMessage).isNotNull();
        assertThat(delegatedMessage.getAddress()).isEqualTo(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by another adapter instance, and the command message is invalid.
     */
    @Test
    public void testMapWithCommandHandlerOnAnotherInstanceWithInvalidMessage() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for another adapter instance (not the local one)
        final String otherAdapterInstance = "otherAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, otherAdapterInstance)));

        // register local command handler - that shall not get used
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, localHandlerCmdContextRef::set);

        // WHEN mapping and delegating the invalid command message
        final Message message = getValidCommandMessage(deviceId);
        message.setSubject(null); // make the message invalid
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the delivery gets REJECTED
        verify(delivery).disposition(any(Rejected.class), eq(true));
        assertThat(localHandlerCmdContextRef.get()).isNull();
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by another adapter instance and where sending the command message to
     * the adapter instance fails.
     */
    @Test
    public void testMapWithCommandHandlerOnAnotherInstanceWithMessageSendingFailed() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for another adapter instance (not the local one)
        final String otherAdapterInstance = "otherAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, otherAdapterInstance)));

        // AND an error when sending the command message to another adapter instance (no credit)
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);

        // register local command handler - that shall not get used
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, deviceId, null, localHandlerCmdContextRef::set);

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the delivery gets RELEASED
        verify(delivery).disposition(any(Released.class), eq(true));
        assertThat(localHandlerCmdContextRef.get()).isNull();
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by another adapter instance, the command handler being set for a gateway.
     */
    @Test
    public void testMapWithCommandHandlerForGatewayOnAnotherInstance() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";

        // GIVEN a gateway commandHandler registered for another adapter instance (not the local one)
        final String otherAdapterInstance = "otherAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(gatewayId, otherAdapterInstance)));

        // AND an ACCEPTED result when sending the command message to another adapter instance
        final ProtonDelivery sendMsgDeliveryUpdate = mock(ProtonDelivery.class);
        when(sendMsgDeliveryUpdate.getRemoteState()).thenReturn(new Accepted());
        when(sendMsgDeliveryUpdate.remotelySettled()).thenReturn(true);
        final AtomicReference<Message> delegatedMessageRef = new AtomicReference<>();
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            delegatedMessageRef.set(invocation.getArgument(0));
            final Handler<ProtonDelivery> dispositionHandler = invocation.getArgument(1);
            dispositionHandler.handle(sendMsgDeliveryUpdate);
            return mock(ProtonDelivery.class);
        });

        // register local command handler - that shall not get used
        final AtomicReference<CommandContext> localHandlerCmdContextRef = new AtomicReference<>();
        adapterInstanceCommandHandler.putDeviceSpecificCommandHandler(Constants.DEFAULT_TENANT, gatewayId, null, localHandlerCmdContextRef::set);

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(Constants.DEFAULT_TENANT, delivery, message);

        // THEN the delivery gets ACCEPTED as well
        verify(delivery).disposition(any(Accepted.class), eq(true));
        assertThat(localHandlerCmdContextRef.get()).isNull();
        final Message delegatedMessage = delegatedMessageRef.get();
        assertThat(delegatedMessage).isNotNull();
        assertThat(delegatedMessage.getAddress()).isEqualTo(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        final String viaProperty = MessageHelper.getApplicationProperty(delegatedMessage.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_CMD_VIA, String.class);
        assertThat(viaProperty).isEqualTo(gatewayId);
    }

    private JsonObject createTargetAdapterInstanceJson(final String deviceId, final String otherAdapterInstance) {
        final JsonObject targetAdapterInstanceJson = new JsonObject();
        targetAdapterInstanceJson.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        targetAdapterInstanceJson.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, otherAdapterInstance);
        return targetAdapterInstanceJson;
    }

    private Message getValidCommandMessage(final String deviceId) {
        final Message message = ProtonHelper.message("input data");
        message.setAddress(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, Constants.DEFAULT_TENANT, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId("the-correlation-id");
        return message;
    }
}
