/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.commandrouter.impl.pubsub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.pubsub.PubSubBasedCommandContext;
import org.eclipse.hono.client.command.pubsub.PubSubBasedCommandResponseSender;
import org.eclipse.hono.client.command.pubsub.PubSubBasedInternalCommandSender;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.pubsub.v1.PubsubMessage;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Verifies behavior or {@link PubSubBasedMappingAndDelegatingCommandHandler}.
 */
public class PubSubBasedMappingAndDelegatingCommandHandlerTest {

    private CommandTargetMapper commandTargetMapper;
    private PubSubBasedInternalCommandSender internalCommandSender;
    private Vertx vertx;
    private String tenantId;
    private String deviceId;
    private String adapterInstanceId;

    private PubSubBasedMappingAndDelegatingCommandHandler commandHandler;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
        adapterInstanceId = UUID.randomUUID().toString();

        final TenantClient tenantClient = mock(TenantClient.class);
        when(tenantClient.get(eq(tenantId), any())).thenReturn(Future.succeededFuture(TenantObject.from(tenantId)));

        commandTargetMapper = mock(CommandTargetMapper.class);
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        internalCommandSender = mock(PubSubBasedInternalCommandSender.class);
        when(internalCommandSender.sendCommand(
                any(CommandContext.class),
                anyString()))
                        .thenReturn(Future.succeededFuture());

        final PubSubBasedCommandResponseSender pubSubBasedCommandResponseSender = mock(
                PubSubBasedCommandResponseSender.class);

        vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);

        final PubSubBasedCommandProcessingQueue commandQueue = new PubSubBasedCommandProcessingQueue(vertx);
        final CommandRouterMetrics metrics = mock(CommandRouterMetrics.class);
        when(metrics.startTimer()).thenReturn(Timer.start());
        final Tracer tracer = TracingMockSupport.mockTracer(TracingMockSupport.mockSpan());
        commandHandler = new PubSubBasedMappingAndDelegatingCommandHandler(
                vertx,
                tenantClient,
                commandQueue,
                commandTargetMapper,
                internalCommandSender,
                metrics,
                tracer,
                pubSubBasedCommandResponseSender);
    }

    /**
     * Verifies the behavior of the
     * {@link PubSubBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(PubsubMessage, String)} method
     * in a scenario with a valid command message.
     */
    @Test
    public void testCommandDelegationForValidCommand() {
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, "command-subject");

        commandHandler.mapAndDelegateIncomingCommandMessage(message, tenantId);

        final ArgumentCaptor<PubSubBasedCommandContext> commandContextArgumentCaptor = ArgumentCaptor
                .forClass(PubSubBasedCommandContext.class);
        verify(internalCommandSender, times(1)).sendCommand(
                commandContextArgumentCaptor.capture(),
                eq(adapterInstanceId));

        final PubSubBasedCommandContext commandContext = commandContextArgumentCaptor.getValue();
        assertNotNull(commandContext);
        assertTrue(commandContext.getCommand().isValid());
        assertEquals(tenantId, commandContext.getCommand().getTenant());
        assertEquals(deviceId, commandContext.getCommand().getDeviceId());
        assertEquals("command-subject", commandContext.getCommand().getName());
    }

    /**
     * Verifies the behavior of the
     * {@link PubSubBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(PubsubMessage, String)} method
     * in a scenario with an invalid command message.
     */
    @Test
    public void testCommandDelegationForInvalidCommandCatchException() {
        final PubsubMessage message = getPubSubMessage(tenantId, null, "command-subject");

        commandHandler.mapAndDelegateIncomingCommandMessage(message, tenantId);

        verify(internalCommandSender, never()).sendCommand(
                any(CommandContext.class),
                anyString());
    }

    /**
     * Verifies the behavior of the
     * {@link PubSubBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(PubsubMessage, String)} method
     * in a scenario with an invalid command message.
     */
    @Test
    public void testCommandDelegationForInvalidCommand() {
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, null);

        commandHandler.mapAndDelegateIncomingCommandMessage(message, tenantId);

        verify(internalCommandSender, never()).sendCommand(
                any(CommandContext.class),
                anyString());
    }

    /**
     * Verifies the behavior of the
     * {@link PubSubBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(PubsubMessage, String)} method
     * in a scenario where mapping/delegation of a valid command message times out.
     */
    @Test
    public void testCommandDelegationTimesOut() {
        VertxMockSupport.runTimersImmediately(vertx);

        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, null);

        final Future<Void> resultFuture = commandHandler.mapAndDelegateIncomingCommandMessage(message, tenantId);

        verify(internalCommandSender, never()).sendCommand(
                any(CommandContext.class),
                anyString());
        assertThat(resultFuture.failed()).isTrue();
    }

    /**
     * Verifies the behavior of the
     * {@link PubSubBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(PubsubMessage, String)} method
     * in a scenario where no target adapter instance is found for the incoming command message.
     */
    @Test
    public void testCommandDelegationWhenNoAdapterInstanceIsFound() {
        final PubsubMessage message = getPubSubMessage(tenantId, deviceId, null);

        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        commandHandler.mapAndDelegateIncomingCommandMessage(message, tenantId);

        verify(internalCommandSender, never()).sendCommand(
                any(CommandContext.class),
                anyString());
    }

    private JsonObject createTargetAdapterInstanceJson(final String deviceId, final String otherAdapterInstance) {
        final JsonObject targetAdapterInstanceJson = new JsonObject();

        targetAdapterInstanceJson.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        targetAdapterInstanceJson.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, otherAdapterInstance);

        return targetAdapterInstanceJson;
    }

    private PubsubMessage getPubSubMessage(final String tenantId, final String deviceId, final String subject) {
        final Map<String, String> attributes = new HashMap<>();
        Optional.ofNullable(deviceId)
                .ifPresent(ok -> attributes.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId));
        Optional.ofNullable(tenantId)
                .ifPresent(ok -> attributes.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId));
        Optional.ofNullable(subject)
                .ifPresent(ok -> attributes.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject));

        return PubsubMessage.newBuilder().putAllAttributes(attributes).build();
    }
}
