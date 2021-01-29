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

package org.eclipse.hono.adapter.client.command.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.CommandHandlers;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.admin.KafkaAdminClient;
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

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        final KafkaAdminClient kafkaAdminClient = mock(KafkaAdminClient.class);
        @SuppressWarnings("unchecked")
        final KafkaConsumer<String, Buffer> kafkaConsumer = mock(KafkaConsumer.class);
        final String adapterInstanceId = "adapterInstanceId";
        final Span span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);
        commandHandlers = new CommandHandlers();
        internalCommandConsumer = new KafkaBasedInternalCommandConsumer(
                kafkaAdminClient,
                kafkaConsumer,
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

        final List<KafkaHeader> headers = List.of(KafkaHeader.header(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId),
                KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId));
        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId, headers);

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler);

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
                getHeaders(tenantId, deviceId, subject));

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
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

        final List<KafkaHeader> headers = new ArrayList<>(getHeaders(tenantId, deviceId, subject));
        headers.add(KafkaHeader.header(MessageHelper.APP_PROPERTY_CMD_VIA, gatewayId));

        final KafkaConsumerRecord<String, Buffer> commandRecord = getCommandRecord(deviceId, headers);

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, gatewayId, null, commandHandler);

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
                getHeaders(tenantId, deviceId, subject));

        final Handler<CommandContext> commandHandler = VertxMockSupport.mockHandler();
        commandHandlers.putCommandHandler(tenantId, deviceId, gatewayId, commandHandler);

        internalCommandConsumer.handleCommandMessage(commandRecord);

        final ArgumentCaptor<CommandContext> commandContextCaptor = ArgumentCaptor.forClass(CommandContext.class);
        verify(commandHandler).handle(commandContextCaptor.capture());
        assertThat(commandContextCaptor.getValue()).isNotNull();
        assertThat(commandContextCaptor.getValue().getCommand().isValid()).isTrue();
        // assert that command is directed at the gateway
        assertThat(commandContextCaptor.getValue().getCommand().getGatewayId()).isEqualTo(gatewayId);
        assertThat(commandContextCaptor.getValue().getCommand().getDeviceId()).isEqualTo(deviceId);
    }

    private List<KafkaHeader> getHeaders(final String tenantId, final String deviceId, final String subject) {
        return List.of(
                KafkaHeader.header(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId),
                KafkaHeader.header(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                KafkaHeader.header(MessageHelper.SYS_PROPERTY_SUBJECT, subject)
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
