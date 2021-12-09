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

package org.eclipse.hono.client.command.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.admin.Admin;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * Tests verifying behavior of {@link KafkaBasedInternalCommandConsumer}.
 *
 */
public class KafkaBasedInternalCommandConsumerTest {

    private KafkaBasedInternalCommandConsumer internalCommandConsumer;
    private CommandHandlers commandHandlers;
    private Context context;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final Admin kafkaAdminClient = mock(Admin.class);
        @SuppressWarnings("unchecked")
        final KafkaConsumer<String, Buffer> kafkaConsumer = mock(KafkaConsumer.class);
        final String adapterInstanceId = "adapterInstanceId";
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);
        context = VertxMockSupport.mockContext(mock(Vertx.class));
        commandHandlers = new CommandHandlers();
        final CommandResponseSender commandResponseSender = mock(CommandResponseSender.class);
        internalCommandConsumer = new KafkaBasedInternalCommandConsumer(
                context,
                kafkaAdminClient,
                kafkaConsumer,
                "testClientId",
                commandResponseSender,
                adapterInstanceId,
                commandHandlers,
                tracer);
    }

    /**
     * Verifies that the consumer handles a command record with missing subject by invoking the matching handler.
     */
    @Test
    void testHandleCommandMessageWithInvalidRecord() {
        // command record with missing subject header
        final String tenantId = "myTenant";
        final String deviceId = "4711";

        final List<KafkaHeader> headers = List.of(
                KafkaRecordHelper.createTenantIdHeader(tenantId),
                KafkaRecordHelper.createDeviceIdHeader(deviceId),
                KafkaRecordHelper.createOriginalPartitionHeader(0),
                KafkaRecordHelper.createOriginalOffsetHeader(0L));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId, headers);

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        // assert that command is not valid
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isFalse();
        assertThat(commandContextCaptor.getValue().getCommand().getInvalidCommandReason()).contains("subject");

    }

    /**
     * Verifies that the consumer handles a valid message by invoking the matching command handler.
     */
    @Test
    void testHandleCommandMessageWithHandlerForDevice() {
        final String tenantId = "myTenant";
        final String deviceId = "4711";
        final String subject = "subject";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId,
                getHeaders(tenantId, deviceId, subject, 0L));

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
    }

    /**
     * Verifies that the consumer doesn't invoke a matching command handler if the command record
     * has a partition offset smaller or equal to one of a command that was already handled.
     */
    @Test
    void testHandleDuplicateCommandMessage() {
        final String tenantId = "myTenant";
        final String deviceId = "4711";
        final String subject = "subject";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId,
                getHeaders(tenantId, deviceId, subject, 10L));

        // 2nd command - with smaller offset, indicating it is a duplicate
        final KafkaConsumerRecord<String, Buffer> commandRecord2 = getCommandRecord(deviceId,
                getHeaders(tenantId, deviceId, subject, 5L));

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);
        internalCommandConsumer.handleCommandMessage(commandRecord2);

        final InOrder inOrder = inOrder(commandHandler);
        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        // first invocation
        inOrder.verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
        // verify there was no second invocation
        inOrder.verify(commandHandler, never()).handle(any());
    }

    /**
     * Verifies that the consumer handles a valid message, targeted at a gateway, by invoking the matching command
     * handler.
     */
    @Test
    void testHandleCommandMessageWithHandlerForGateway() {
        final String tenantId = "myTenant";
        final String deviceId = "4711";
        final String gatewayId = "gw-1";
        final String subject = "subject";

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(tenantId, deviceId, subject, 0L));
        headers.add(KafkaRecordHelper.createViaHeader(gatewayId));

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId, headers);

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, gatewayId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    /**
     * Verifies that the consumer handles a valid message, for which the matching command handler is associated
     * with a gateway, by invoking the handler and adopting the gateway identifier in the command object.
     */
    @Test
    void testHandleCommandMessageWithHandlerForGatewayAndSpecificDevice() {
        final String tenantId = "myTenant";
        final String deviceId = "4711";
        final String gatewayId = "gw-1";
        final String subject = "subject";

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId,
                getHeaders(tenantId, deviceId, subject, 0L));

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, gatewayId, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    private List<KafkaHeader> getHeaders(final String tenantId, final String deviceId, final String subject, final long originalPartitionOffset) {
        return List.of(
                KafkaRecordHelper.createTenantIdHeader(tenantId),
                KafkaRecordHelper.createDeviceIdHeader(deviceId),
                KafkaRecordHelper.createSubjectHeader(subject),
                KafkaRecordHelper.createOriginalPartitionHeader(0),
                KafkaRecordHelper.createOriginalOffsetHeader(originalPartitionOffset)
        );
    }

    @SuppressWarnings("unchecked")
    private KafkaConsumerRecord<String, Buffer> getCommandRecord(final String key, final List<KafkaHeader> headers) {
        final String adapterInstanceId = "the-adapter-instance-id";
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
        final KafkaConsumerRecord<String, Buffer> consumerRecord = mock(KafkaConsumerRecord.class);
        when(consumerRecord.headers()).thenReturn(headers);
        when(consumerRecord.topic()).thenReturn(topic);
        when(consumerRecord.key()).thenReturn(key);
        return consumerRecord;
    }
}
