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
package org.eclipse.hono.commandrouter.impl.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies behavior of {@link KafkaBasedMappingAndDelegatingCommandHandler}.
 */
public class KafkaBasedMappingAndDelegatingCommandHandlerTest {

    private TenantClient tenantClient;
    private CommandTargetMapper commandTargetMapper;
    private KafkaBasedMappingAndDelegatingCommandHandler cmdHandler;
    private KafkaBasedInternalCommandSender internalCommandSender;
    private String tenantId;
    private String deviceId;
    private String adapterInstanceId;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        tenantId = UUID.randomUUID().toString();
        deviceId = UUID.randomUUID().toString();
        adapterInstanceId = UUID.randomUUID().toString();

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(eq(tenantId), any())).thenReturn(Future.succeededFuture(TenantObject.from(tenantId)));

        commandTargetMapper = mock(CommandTargetMapper.class);
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        internalCommandSender = mock(KafkaBasedInternalCommandSender.class);

        cmdHandler = new KafkaBasedMappingAndDelegatingCommandHandler(tenantClient, commandTargetMapper,
                internalCommandSender, TracingMockSupport.mockTracer(TracingMockSupport.mockSpan()));
    }

    /**
     * Verifies the behaviour of the 
     * {@link KafkaBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(KafkaConsumerRecord)}
     * method in a scenario with a valid command record.
     */
    @Test
    public void testCommandDelegationForValidCommand() {
        // GIVEN a valid command record
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, deviceId, "cmd-subject");

        // WHEN mapping and delegating the command
        cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord);

        final ArgumentCaptor<KafkaBasedCommandContext> commandContextArgumentCaptor = ArgumentCaptor
                .forClass(KafkaBasedCommandContext.class);
        // THEN the message is properly delegated
        verify(internalCommandSender, times(1)).sendCommand(commandContextArgumentCaptor.capture(),
                eq(adapterInstanceId));

        final KafkaBasedCommandContext commandContext = commandContextArgumentCaptor.getValue();
        assertNotNull(commandContext);
        assertTrue(commandContext.getCommand().isValid());
        assertEquals(tenantId, commandContext.getCommand().getTenant());
        assertEquals(deviceId, commandContext.getCommand().getDeviceId());
        assertEquals("cmd-subject", commandContext.getCommand().getName());
    }

    /**
     * Verifies the behaviour of the 
     * {@link KafkaBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(KafkaConsumerRecord)}
     * method in a scenario with an invalid command record.
     */
    @Test
    public void testCommandDelegationForInValidCommand() {
        // GIVEN a command record that does not contain a device ID
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, deviceId, null);

        // WHEN mapping and delegating the command
        cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord);

        // THEN the message is not delegated
        verify(internalCommandSender, never()).sendCommand(any(), any());
    }

    /**
     * Verifies the behaviour of the 
     * {@link KafkaBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(KafkaConsumerRecord)}
     * method in a scenario where no target adapter instance is found for the incoming command record.
     */
    @Test
    public void testCommandDelegationWhenNoAdapterInstanceIsFound() {
        // GIVEN a valid command record
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, deviceId, "cmd-subject");

        //WHEN no target adapter instance is found
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId), any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        // WHEN mapping and delegating the command
        cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord);

        // THEN the message is not delegated
        verify(internalCommandSender, never()).sendCommand(any(), any());
    }

    /**
     * Verifies the behaviour of the 
     * {@link KafkaBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(KafkaConsumerRecord)}
     * method in a scenario where there is no device id in the incoming command record.
     */
    @Test
    public void testCommandRecordWithoutDeviceId() {
        // GIVEN a command record that does not contain a device ID
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, null, "cmd-subject");

        // WHEN mapping and delegating the command
        cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord);

        // THEN the message is not delegated
        verify(internalCommandSender, never()).sendCommand(any(), any());
    }

    private JsonObject createTargetAdapterInstanceJson(final String deviceId, final String otherAdapterInstance) {
        final JsonObject targetAdapterInstanceJson = new JsonObject();

        targetAdapterInstanceJson.put(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        targetAdapterInstanceJson.put(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID, otherAdapterInstance);

        return targetAdapterInstanceJson;
    }

    @SuppressWarnings("unchecked")
    private KafkaConsumerRecord<String, Buffer> getCommandRecord(final String tenantId, final String deviceId,
            final String subject) {
        final List<KafkaHeader> headers = new ArrayList<>();
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();
        final KafkaConsumerRecord<String, Buffer> consumerRecord = mock(KafkaConsumerRecord.class);

        Optional.ofNullable(deviceId)
                .ifPresent(ok -> headers.add(KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)));
        Optional.ofNullable(subject)
                .ifPresent(ok -> headers.add(KafkaHeader.header(MessageHelper.SYS_PROPERTY_SUBJECT, subject)));
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.topic()).thenReturn(topic);
        when(consumerRecord.key()).thenReturn(deviceId);

        return consumerRecord;
    }
}
