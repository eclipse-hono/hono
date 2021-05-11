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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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

import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Verifies behavior of {@link KafkaBasedMappingAndDelegatingCommandHandler}.
 */
@ExtendWith(VertxExtension.class)
public class KafkaBasedMappingAndDelegatingCommandHandlerTest {

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

        final TenantClient tenantClient = mock(TenantClient.class);
        when(tenantClient.get(eq(tenantId), any())).thenReturn(Future.succeededFuture(TenantObject.from(tenantId)));

        commandTargetMapper = mock(CommandTargetMapper.class);
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId), any()))
                .thenReturn(Future.succeededFuture(createTargetAdapterInstanceJson(deviceId, adapterInstanceId)));

        internalCommandSender = mock(KafkaBasedInternalCommandSender.class);
        when(internalCommandSender.sendCommand(any(), any())).thenReturn(Future.succeededFuture());

        final Context context = VertxMockSupport.mockContext(mock(Vertx.class));
        final KafkaCommandProcessingQueue commandQueue = new KafkaCommandProcessingQueue(context);
        cmdHandler = new KafkaBasedMappingAndDelegatingCommandHandler(tenantClient, commandQueue, commandTargetMapper,
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
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, deviceId, "cmd-subject", 0, 0);

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
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, deviceId, null, 0, 0);

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
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, deviceId, "cmd-subject", 0, 0);

        // WHEN no target adapter instance is found
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
     * method in a scenario where the mapping operation for one command completes earlier than for a previously received
     * command. The order in which commands are then delegated to the target adapter instance has to be the same
     * as the order in which commands were received.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testIncomingCommandOrderIsPreservedWhenDelegating(final VertxTestContext ctx) {
        final String deviceId1 = "device1";
        final String deviceId2 = "device2";
        final String deviceId3 = "device3";
        final String deviceId4 = "device4";

        // GIVEN valid command records
        final KafkaConsumerRecord<String, Buffer> commandRecord1 = getCommandRecord(tenantId, deviceId1, "subject1", 0, 1);
        final KafkaConsumerRecord<String, Buffer> commandRecord2 = getCommandRecord(tenantId, deviceId2, "subject2", 0, 2);
        final KafkaConsumerRecord<String, Buffer> commandRecord3 = getCommandRecord(tenantId, deviceId3, "subject3", 0, 3);
        final KafkaConsumerRecord<String, Buffer> commandRecord4 = getCommandRecord(tenantId, deviceId4, "subject4", 0, 4);

        // WHEN getting the target adapter instances for the commands results in different delays for each command
        // so that the invocations are completed with the order: commandRecord3, commandRecord2, commandRecord1, commandRecord4
        final Promise<JsonObject> resultForCommand1 = Promise.promise();
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId1), any()))
                .thenReturn(resultForCommand1.future());
        final Promise<JsonObject> resultForCommand2 = Promise.promise();
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId2), any()))
                .thenReturn(resultForCommand2.future());
        final Promise<JsonObject> resultForCommand3 = Promise.promise();
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId3), any()))
                .thenReturn(resultForCommand3.future());
        doAnswer(invocation -> {
            resultForCommand3.complete(createTargetAdapterInstanceJson(deviceId3, adapterInstanceId));
            resultForCommand2.complete(createTargetAdapterInstanceJson(deviceId2, adapterInstanceId));
            resultForCommand1.complete(createTargetAdapterInstanceJson(deviceId1, adapterInstanceId));
            return Future.succeededFuture(createTargetAdapterInstanceJson(deviceId4, adapterInstanceId));
        }).when(commandTargetMapper).getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId4), any());

        // WHEN mapping and delegating the commands
        final Future<Void> cmd1Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord1);
        final Future<Void> cmd2Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord2);
        final Future<Void> cmd3Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord3);
        final Future<Void> cmd4Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord4);

        // THEN the messages are delegated in the original order
        CompositeFuture.all(cmd1Future, cmd2Future, cmd3Future, cmd4Future)
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
                        verify(internalCommandSender, times(4)).sendCommand(commandContextCaptor.capture(), any());
                        final List<CommandContext> capturedCommandContexts = commandContextCaptor.getAllValues();
                        assertThat(capturedCommandContexts.get(0).getCommand().getDeviceId()).isEqualTo(deviceId1);
                        assertThat(capturedCommandContexts.get(1).getCommand().getDeviceId()).isEqualTo(deviceId2);
                        assertThat(capturedCommandContexts.get(2).getCommand().getDeviceId()).isEqualTo(deviceId3);
                        assertThat(capturedCommandContexts.get(3).getCommand().getDeviceId()).isEqualTo(deviceId4);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies the behaviour of the
     * {@link KafkaBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(KafkaConsumerRecord)}
     * method in a scenario where the rather long-running processing of a command delays subsequent, already mapped
     * commands from getting delegated to the target adapter instance. After the processing of the first command finally
     * resulted in an error, the subsequent commands shall get delegated in the correct order.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testCommandDelegationOrderWithMappingFailedForFirstEntry(final VertxTestContext ctx) {
        final String deviceId1 = "device1";
        final String deviceId2 = "device2";
        final String deviceId3 = "device3";
        final String deviceId4 = "device4";

        // GIVEN valid command records
        final KafkaConsumerRecord<String, Buffer> commandRecord1 = getCommandRecord(tenantId, deviceId1, "subject1", 0, 1);
        final KafkaConsumerRecord<String, Buffer> commandRecord2 = getCommandRecord(tenantId, deviceId2, "subject2", 0, 2);
        final KafkaConsumerRecord<String, Buffer> commandRecord3 = getCommandRecord(tenantId, deviceId3, "subject3", 0, 3);
        final KafkaConsumerRecord<String, Buffer> commandRecord4 = getCommandRecord(tenantId, deviceId4, "subject4", 0, 4);

        // WHEN getting the target adapter instances for the commands results in different delays for each command
        // so that the invocations are completed with the order: commandRecord3, commandRecord2, commandRecord1 (failed), commandRecord4
        // with command 1 getting failed
        final Promise<JsonObject> resultForCommand1 = Promise.promise();
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId1), any()))
                .thenReturn(resultForCommand1.future());
        final Promise<JsonObject> resultForCommand2 = Promise.promise();
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId2), any()))
                .thenReturn(resultForCommand2.future());
        final Promise<JsonObject> resultForCommand3 = Promise.promise();
        when(commandTargetMapper.getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId3), any()))
                .thenReturn(resultForCommand3.future());
        doAnswer(invocation -> {
            resultForCommand3.complete(createTargetAdapterInstanceJson(deviceId3, adapterInstanceId));
            resultForCommand2.complete(createTargetAdapterInstanceJson(deviceId2, adapterInstanceId));
            resultForCommand1.fail("mapping of command 1 failed for some reason");
            return Future.succeededFuture(createTargetAdapterInstanceJson(deviceId4, adapterInstanceId));
        }).when(commandTargetMapper).getTargetGatewayAndAdapterInstance(eq(tenantId), eq(deviceId4), any());

        // WHEN mapping and delegating the commands
        final Future<Void> cmd1Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord1);
        final Future<Void> cmd2Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord2);
        final Future<Void> cmd3Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord3);
        final Future<Void> cmd4Future = cmdHandler.mapAndDelegateIncomingCommandMessage(commandRecord4);

        // THEN the messages are delegated in the original order, with command 1 left out because it timed out
        CompositeFuture.all(cmd2Future, cmd3Future, cmd4Future)
                .onComplete(ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        assertThat(cmd1Future.failed()).isTrue();
                        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
                        verify(internalCommandSender, times(3)).sendCommand(commandContextCaptor.capture(), any());
                        final List<CommandContext> capturedCommandContexts = commandContextCaptor.getAllValues();
                        assertThat(capturedCommandContexts.get(0).getCommand().getDeviceId()).isEqualTo(deviceId2);
                        assertThat(capturedCommandContexts.get(1).getCommand().getDeviceId()).isEqualTo(deviceId3);
                        assertThat(capturedCommandContexts.get(2).getCommand().getDeviceId()).isEqualTo(deviceId4);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies the behaviour of the 
     * {@link KafkaBasedMappingAndDelegatingCommandHandler#mapAndDelegateIncomingCommandMessage(KafkaConsumerRecord)}
     * method in a scenario where there is no device id in the incoming command record.
     */
    @Test
    public void testCommandRecordWithoutDeviceId() {
        // GIVEN a command record that does not contain a device ID
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(tenantId, null, "cmd-subject", 0, 0);

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
            final String subject, final int partition, final long offset) {
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
        when(consumerRecord.partition()).thenReturn(partition);
        when(consumerRecord.offset()).thenReturn(offset);

        return consumerRecord;
    }
}
