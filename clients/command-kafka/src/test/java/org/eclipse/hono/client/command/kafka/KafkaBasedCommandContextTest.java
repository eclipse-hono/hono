/**
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
 */


package org.eclipse.hono.client.command.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic.Type;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;


/**
 * Tests verifying behavior of {@link KafkaBasedCommandContext}.
 *
 */
class KafkaBasedCommandContextTest {

    private CommandResponseSender responseSender;

    @BeforeEach
    void setUp() {
        responseSender = mock(CommandResponseSender.class);
        when(responseSender.sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(CommandResponse.class),
                any()))
            .thenReturn(Future.succeededFuture());
    }

    private KafkaBasedCommand getRequestResponseCommand(final String tenantId, final String deviceId) {

        final KafkaConsumerRecord<String, Buffer> record = KafkaClientUnitTestHelper.newMockConsumerRecord(
                Type.COMMAND.prefix + tenantId,
                deviceId,
                List.of(
                        KafkaRecordHelper.createCorrelationIdHeader("corrId"),
                        KafkaRecordHelper.createResponseRequiredHeader(true),
                        KafkaRecordHelper.createDeviceIdHeader(deviceId),
                        KafkaRecordHelper.createSubjectHeader("go")));
        return KafkaBasedCommand.from(record);
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextGetsRejected() {

        testErrorIsSentOnCommandResponseTopic(
                context -> context.reject(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)),
                commandResponse -> {
                    assertThat(commandResponse.getStatus()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                });
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextGetsReleased() {

        testErrorIsSentOnCommandResponseTopic(
                context -> context.release(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)),
                commandResponse -> {
                    assertThat(commandResponse.getStatus()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                });
    }

    private void testErrorIsSentOnCommandResponseTopic(
            final Consumer<KafkaBasedCommandContext> contextHandler,
            final Consumer<CommandResponse> responseAssertions) {

        final String deviceId = "device-id";
        final var command = getRequestResponseCommand(Constants.DEFAULT_TENANT, deviceId);
        final var context = new KafkaBasedCommandContext(command, responseSender, NoopSpan.INSTANCE);

        contextHandler.accept(context);

        final ArgumentCaptor<CommandResponse> commandResponse = ArgumentCaptor.forClass(CommandResponse.class);
        verify(responseSender).sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                commandResponse.capture(),
                any());
        assertThat(commandResponse.getValue().getCorrelationId()).isEqualTo("corrId");
        responseAssertions.accept(commandResponse.getValue());
    }
}
