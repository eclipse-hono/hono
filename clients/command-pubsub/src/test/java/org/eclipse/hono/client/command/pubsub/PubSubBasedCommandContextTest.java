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
package org.eclipse.hono.client.command.pubsub;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;

/**
 * Verifies behavior of {@link PubSubBasedCommandContext}.
 *
 */
public class PubSubBasedCommandContextTest {

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

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextWithResponseRequiredGetsRejected() {
        testErrorIsSentOnCommandResponseTopic(
                context -> context.reject(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)),
                commandResponse -> assertThat(commandResponse.getStatus())
                        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST), "true", "false");
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextWithAckRequiredGetsRejected() {
        testErrorIsSentOnCommandResponseTopic(
                context -> context.reject(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)),
                commandResponse -> assertThat(commandResponse.getStatus())
                        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST), "false", "true");
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextWithResponseRequiredGetsReleased() {
        testErrorIsSentOnCommandResponseTopic(
                context -> context.release(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)),
                commandResponse -> assertThat(commandResponse.getStatus())
                        .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE), "true", "false");
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextWithAckRequiredGetsReleased() {
        testErrorIsSentOnCommandResponseTopic(
                context -> context.release(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)),
                commandResponse -> assertThat(commandResponse.getStatus())
                        .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE), "false", "true");
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextWithResponseRequiredGetsModified() {
        testErrorIsSentOnCommandResponseTopic(
                context -> context.modify(true, true),
                commandResponse -> assertThat(commandResponse.getStatus())
                        .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND), "true", "false");
    }

    @Test
    void testErrorIsSentOnCommandResponseTopicWhenContextWithAckRequiredGetsModified() {
        testErrorIsSentOnCommandResponseTopic(
                context -> context.modify(true, true),
                commandResponse -> assertThat(commandResponse.getStatus())
                        .isEqualTo(HttpURLConnection.HTTP_NOT_FOUND), "false", "true");
    }

    @Test
    void testNoErrorIsSentOnCommandResponseTopicWhenContextWithResponseRequiredGetsAccepted() {
        final var command = getCommand("true", "false");
        final Span span = TracingMockSupport.mockSpan();
        final var context = new PubSubBasedCommandContext(command, responseSender, span);
        context.accept();
        verify(span).finish();
    }

    @Test
    void testNoErrorIsSentOnCommandResponseTopicWhenContextWithAckRequiredGetsAccepted() {
        final var command = getCommand("false", "true");
        final Span span = TracingMockSupport.mockSpan();
        final var context = new PubSubBasedCommandContext(command, responseSender, span);
        context.accept();
        verify(span).finish();
    }

    private void testErrorIsSentOnCommandResponseTopic(
            final Consumer<PubSubBasedCommandContext> contextHandler,
            final Consumer<CommandResponse> responseAssertions,
            final String responseRequired, final String ackRequired) {

        final var command = getCommand(responseRequired, ackRequired);
        final var context = new PubSubBasedCommandContext(command, responseSender, NoopSpan.INSTANCE);
        contextHandler.accept(context);

        final ArgumentCaptor<CommandResponse> commandResponse = ArgumentCaptor.forClass(CommandResponse.class);
        verify(responseSender).sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                commandResponse.capture(),
                any());
        assertThat(commandResponse.getValue().getCorrelationId()).isEqualTo("my-correlation-id");
        responseAssertions.accept(commandResponse.getValue());
    }

    private PubSubBasedCommand getCommand(final String responseRequired, final String ackRequired) {

        final String correlationId = "my-correlation-id";
        final String deviceId = "test-device";
        final String tenantId = "test-tenant";
        final String subject = "test-subject";

        final Map<String, String> attributes = new HashMap<>();
        attributes.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        attributes.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        attributes.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject);
        attributes.put(PubSubMessageHelper.PUBSUB_PROPERTY_RESPONSE_REQUIRED, responseRequired);
        attributes.put(PubSubMessageHelper.PUBSUB_PROPERTY_ACK_REQUIRED, ackRequired);
        attributes.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId);

        final PubsubMessage message = PubsubMessage.newBuilder().putAllAttributes(attributes).build();
        return PubSubBasedCommand.from(message, tenantId);
    }
}
