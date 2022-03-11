/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl.amqp;

import static org.mockito.ArgumentMatchers.any;
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
import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * Verifies behavior of {@link ProtonBasedMappingAndDelegatingCommandHandler}.
 */
public class ProtonBasedMappingAndDelegatingCommandHandlerTest {

    private String tenantId;
    private TenantClient tenantClient;
    private CommandTargetMapper commandTargetMapper;
    private ProtonBasedMappingAndDelegatingCommandHandler mappingAndDelegatingCommandHandler;
    // sender used in the DelegatedCommandSender
    private ProtonSender sender;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {

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

        final ClientConfigProperties props = new ClientConfigProperties();
        props.setSendMessageTimeout(0);
        final HonoConnection connection = mockHonoConnection(vertx, props);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        sender = mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));

        tenantId = UUID.randomUUID().toString();
        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(eq(tenantId), any())).thenReturn(Future.succeededFuture(TenantObject.from(tenantId)));
        commandTargetMapper = mock(CommandTargetMapper.class);

        final CommandRouterMetrics metrics = mock(CommandRouterMetrics.class);
        when(metrics.startTimer()).thenReturn(Timer.start());
        mappingAndDelegatingCommandHandler = new ProtonBasedMappingAndDelegatingCommandHandler(tenantClient,
                connection, commandTargetMapper, metrics);
    }

    /**
     * Verifies that a command message with an address that doesn't contain a device ID
     * gets rejected.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMapForMessageHavingAddressWithoutDeviceId() {

        // GIVEN a command message with an address that does not
        // contain a device ID
        final String deviceId = "4711";
        final Message message = getValidCommandMessage(deviceId);
        message.setAddress(String.format("%s/%s", CommandConstants.COMMAND_ENDPOINT, tenantId));

        // WHEN mapping and delegating the command
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the disposition is REJECTED
        verify(delivery).disposition(
                argThat(state -> AmqpUtils.AMQP_BAD_REQUEST.equals(((Rejected) state).getError().getCondition())),
                eq(true));
        // and the message is not being delegated
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }

    /**
     * Verifies that a command message with an address that contains a tenant which doesn't
     * match the scope of the command receiver link gets rejected.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMapForMessageHavingAddressWithInvalidTenant() {

        // GIVEN a command message with an address that contains an
        // invalid tenant
        final String deviceId = "4711";
        final Message message = getValidCommandMessage(deviceId);
        message.setAddress(String.format("%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, "wrong-tenant", deviceId));

        // WHEN mapping and delegating the command
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the disposition is REJECTED
        verify(delivery).disposition(
                argThat(state -> AmqpError.UNAUTHORIZED_ACCESS.equals(((Rejected) state).getError().getCondition())),
                eq(true));
        // and the message is not being delegated
        verify(sender, never()).send(any(Message.class), any(Handler.class));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * no command handling adapter instance is found for the command message device and the command message
     * is invalid.
     */
    @Test
    public void testMapWithNoAdapterInstanceFoundAndMessageInvalid() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for some adapter instance
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, "someAdapterInstanceId")));

        // WHEN mapping and delegating an invalid command message
        final Message message = getValidCommandMessage(deviceId);
        message.setSubject(null); // make the message invalid
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

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
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the disposition is RELEASED
        verify(delivery).disposition(any(Released.class), eq(true));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by some adapter instance.
     */
    @Test
    public void testMapWithCommandHandlerOnAnotherInstance() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for some adapter instance
        final String someAdapterInstance = "someAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, someAdapterInstance)));

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

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the delivery gets ACCEPTED as well
        verify(delivery).disposition(any(Accepted.class), eq(true));
        final Message delegatedMessage = delegatedMessageRef.get();
        assertThat(delegatedMessage).isNotNull();
        assertThat(delegatedMessage.getAddress()).isEqualTo(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by some adapter instance, and the command message is invalid.
     */
    @Test
    public void testMapWithCommandHandlerOnAnotherInstanceWithInvalidMessage() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for some adapter instance
        final String someAdapterInstance = "someAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, someAdapterInstance)));

        // WHEN mapping and delegating the invalid command message
        final Message message = getValidCommandMessage(deviceId);
        message.setSubject(null); // make the message invalid
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the delivery gets REJECTED
        verify(delivery).disposition(any(Rejected.class), eq(true));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by some adapter instance and where sending the command message to
     * the adapter instance fails.
     */
    @Test
    public void testMapWithCommandHandlerOnAnotherInstanceWithMessageSendingFailed() {
        final String deviceId = "4711";

        // GIVEN a deviceId commandHandler registered for some adapter instance
        final String someAdapterInstance = "someAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, someAdapterInstance)));

        // AND an error when sending the command message to that adapter instance (no credit)
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the delivery gets RELEASED
        verify(delivery).disposition(any(Released.class), eq(true));
    }

    /**
     * Verifies the behaviour of the <em>mapAndDelegateIncomingCommandMessage</em> method in a scenario where
     * the command shall get handled by some adapter instance, the command handler being set for a gateway.
     */
    @Test
    public void testMapWithCommandHandlerForGatewayOnAnotherInstance() {
        final String deviceId = "4711";
        final String gatewayId = "gw-1";

        // GIVEN a gateway commandHandler registered for some adapter instance
        final String someAdapterInstance = "someAdapterInstance";
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(anyString(), anyString(), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(gatewayId, someAdapterInstance)));

        // AND an ACCEPTED result when sending the command message to that adapter instance
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

        // WHEN mapping and delegating the command message
        final Message message = getValidCommandMessage(deviceId);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);

        // THEN the delivery gets ACCEPTED as well
        verify(delivery).disposition(any(Accepted.class), eq(true));
        final Message delegatedMessage = delegatedMessageRef.get();
        assertThat(delegatedMessage).isNotNull();
        assertThat(delegatedMessage.getAddress()).isEqualTo(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId));
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
                CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId));
        message.setSubject("doThis");
        message.setCorrelationId("the-correlation-id");
        return message;
    }

    private static ProtonSender mockProtonSender() {
        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(sender.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        when(sender.getTarget()).thenReturn(mock(Target.class));
        return sender;
    }

    private static <T> HonoConnection mockHonoConnection(final Vertx vertx, final ClientConfigProperties props) {
        final Tracer tracer = NoopTracerFactory.create();
        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(connection.getConfig()).thenReturn(props);
        when(connection.getTracer()).thenReturn(tracer);
        when(connection.executeOnContext(VertxMockSupport.anyHandler())).then(invocation -> {
            final Promise<T> result = Promise.promise();
            final Handler<Future<T>> handler = invocation.getArgument(0);
            handler.handle(result.future());
            return result.future();
        });
        return connection;
    }

}
