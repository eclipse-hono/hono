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

package org.eclipse.hono.client.command.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.Consumer;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
    private TenantClient tenantClient;
    private CommandResponseSender commandResponseSender;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final Admin kafkaAdminClient = mock(Admin.class);
        @SuppressWarnings("unchecked")
        final Consumer<String, Buffer> kafkaConsumer = mock(Consumer.class);
        final String adapterInstanceId = "adapterInstanceId";
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);
        context = VertxMockSupport.mockContext(mock(Vertx.class));
        commandHandlers = new CommandHandlers();
        tenantClient = mock(TenantClient.class);
        doAnswer(invocation -> {
            final String tenantId = invocation.getArgument(0);
            return Future.succeededFuture(TenantObject.from(tenantId));
        }).when(tenantClient).get(anyString(), any());
        commandResponseSender = mock(CommandResponseSender.class);
        when(commandResponseSender.sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(CommandResponse.class),
                any()))
            .thenReturn(Future.succeededFuture());
        internalCommandConsumer = new KafkaBasedInternalCommandConsumer(
                context,
                kafkaAdminClient,
                kafkaConsumer,
                tenantClient,
                commandResponseSender,
                adapterInstanceId,
                commandHandlers,
                tracer);
    }

    /**
     * Verifies that an error response is sent to the application if the tenant of the target device
     * is unknown or cannot be retrieved.
     */
    @ParameterizedTest
    @ValueSource(ints = { HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_UNAVAILABLE })
    void testHandleCommandMessageSendErrorResponse(final int tenantServiceErrorCode) {

        final String tenantId = "myTenant";
        final String deviceId = "4711";
        final String subject = "subject";

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);
        when(tenantClient.get(eq("myTenant"), any())).thenReturn(
                Future.failedFuture(StatusCodeMapper.from(tenantServiceErrorCode, "failed to retrieve tenant")));

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId,
                getHeaders(tenantId, deviceId, subject, 0L));

        internalCommandConsumer.handleCommandMessage(commandRecord);

        verify(commandHandler, never()).apply(any(KafkaBasedCommandContext.class));
        verify(commandResponseSender).sendCommandResponse(
                argThat(t -> t.getTenantId().equals("myTenant")),
                argThat(r -> r.getDeviceId().equals("4711")),
                argThat(cr -> cr.getStatus() == tenantServiceErrorCode),
                any());
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

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
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

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
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

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);
        internalCommandConsumer.handleCommandMessage(commandRecord2);

        final InOrder inOrder = inOrder(commandHandler);
        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        // first invocation
        inOrder.verify(commandHandler).apply(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
        // verify there was no second invocation
        inOrder.verify(commandHandler, never()).apply(any());
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

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(tenantId, gatewayId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
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

        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        when(commandHandler.apply(any())).thenReturn(Future.succeededFuture());
        commandHandlers.putCommandHandler(tenantId, deviceId, gatewayId, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).apply(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    private List<KafkaHeader> getHeaders(
            final String tenantId,
            final String deviceId,
            final String subject,
            final long originalPartitionOffset) {

        return List.of(
                KafkaRecordHelper.createTenantIdHeader(tenantId),
                KafkaRecordHelper.createDeviceIdHeader(deviceId),
                KafkaRecordHelper.createSubjectHeader(subject),
                KafkaRecordHelper.createCorrelationIdHeader("corrId"),
                KafkaRecordHelper.createResponseRequiredHeader(true),
                KafkaRecordHelper.createOriginalPartitionHeader(0),
                KafkaRecordHelper.createOriginalOffsetHeader(originalPartitionOffset)
        );
    }

    private KafkaConsumerRecord<String, Buffer> getCommandRecord(final String key, final List<KafkaHeader> headers) {
        final String adapterInstanceId = "the-adapter-instance-id";
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
        return KafkaClientUnitTestHelper.newMockConsumerRecord(topic, key, headers);
    }
}
