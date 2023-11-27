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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.pubsub.PubSubBasedAdminClientManager;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberClient;
import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberFactory;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Tracer;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Verifies behavior of {@link PubSubBasedInternalCommandConsumer}.
 */
public class PubSubBasedInternalCommandConsumerTest {

    private final String deviceId = "test-device";
    private final String tenantId = "test-tenant";
    private final String subject = "test-subject";

    private final String adapterInstanceId = "test-adapter";

    private final String topicAndSubscription = String.format("%s.%s", adapterInstanceId,
            CommandConstants.INTERNAL_COMMAND_ENDPOINT);

    private PubSubSubscriberClient subscriber;

    private CommandResponseSender commandResponseSender;

    private CommandHandlers commandHandlers;

    private TenantClient tenantClient;

    private AckReplyConsumer ackReplyConsumer;

    private PubSubBasedInternalCommandConsumer internalCommandConsumer;

    @BeforeEach
    void setUp() {
        final Vertx vertxMock = mock(Vertx.class);
        commandResponseSender = mock(CommandResponseSender.class);

        final Tracer tracer = TracingMockSupport.mockTracer(TracingMockSupport.mockSpan());
        commandHandlers = new CommandHandlers();
        tenantClient = mock(TenantClient.class);
        ackReplyConsumer = mock(AckReplyConsumer.class);

        doNothing().when(ackReplyConsumer).ack();

        doAnswer(invocation -> {
            final String tenantId = invocation.getArgument(0);
            return Future.succeededFuture(TenantObject.from(tenantId));
        }).when(tenantClient).get(anyString(), any());

        when(commandResponseSender.sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(CommandResponse.class),
                any())).thenReturn(Future.succeededFuture());

        final PubSubBasedAdminClientManager adminClientManager = mock(PubSubBasedAdminClientManager.class);
        when(adminClientManager
                .getOrCreateTopic(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId))
                .thenReturn(Future.succeededFuture(topicAndSubscription));
        when(adminClientManager
                .getOrCreateSubscription(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId))
                .thenReturn(Future.succeededFuture(topicAndSubscription));

        subscriber = mock(PubSubSubscriberClient.class);
        when(subscriber.subscribe(true)).thenReturn(Future.succeededFuture());

        final MessageReceiver receiver = mock(MessageReceiver.class);
        final PubSubSubscriberFactory subscriberFactory = mock(PubSubSubscriberFactory.class);
        when(subscriberFactory.getOrCreateSubscriber(topicAndSubscription, receiver))
                .thenReturn(subscriber);

        internalCommandConsumer = new PubSubBasedInternalCommandConsumer(
                commandResponseSender,
                vertxMock,
                adapterInstanceId,
                commandHandlers,
                tenantClient,
                tracer,
                subscriberFactory,
                adminClientManager,
                receiver);

    }

    /**
     * Verifies that the subscriber is subscribing when PubSubBasedInternalCommandConsumer is started.
     */
    @Test
    public void testThatSubscriberSubscribesOnStart() {
        internalCommandConsumer.start();
        verify(subscriber).subscribe(true);
    }

    /**
     * Verifies that an error response is sent to the application if the tenant of the target device is unknown or
     * cannot be retrieved.
     */
    @Test
    public void testHandleCommandMessageSendErrorResponse() {
        final PubsubMessage message = getPubSubMessage(subject, "true");
        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        final Context context = VertxMockSupport.mockContext(mock(Vertx.class));
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        when(tenantClient.get(eq(tenantId), any())).thenReturn(
                Future.failedFuture(
                        StatusCodeMapper.from(HttpURLConnection.HTTP_UNAVAILABLE, "failed to retrieve tenant")));

        internalCommandConsumer.handleCommandMessage(message, ackReplyConsumer);
        verify(ackReplyConsumer).ack();
        verify(commandHandler, never()).apply(any(PubSubBasedCommandContext.class));
        verify(commandResponseSender).sendCommandResponse(
                argThat(t -> t.getTenantId().equals(tenantId)),
                argThat(r -> r.getDeviceId().equals(deviceId)),
                argThat(cr -> cr.getStatus() == HttpURLConnection.HTTP_UNAVAILABLE),
                any());
    }

    /**
     * Verifies that the consumer handles an invalid command message with missing subject by invoking the matching
     * handler.
     */
    @Test
    public void testHandleCommandMessageWithInvalidAttribute() {
        final PubsubMessage message = getPubSubMessage(null, "true");
        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        final Context context = VertxMockSupport.mockContext(mock(Vertx.class));
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(message, ackReplyConsumer);

        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor
                .forClass(CommandContext.class);
        verify(ackReplyConsumer).ack();
        verify(commandHandler).apply(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue()).isNotNull();
        assertThat(commandContextArgumentCaptor.getValue().getCommand().isValid()).isFalse();
        assertThat(commandContextArgumentCaptor.getValue().getCommand().getInvalidCommandReason())
                .contains("subject not set");
    }

    /**
     * Verifies that the consumer handles a valid message by invoking the matching command handler.
     */
    @Test
    public void testHandleCommandMessageWithHandlerForDevice() {
        final PubsubMessage message = getPubSubMessage(subject, "false");
        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        final Context context = VertxMockSupport.mockContext(mock(Vertx.class));
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        internalCommandConsumer.handleCommandMessage(message, ackReplyConsumer);

        final ArgumentCaptor<CommandContext> commandContextArgumentCaptor = ArgumentCaptor
                .forClass(CommandContext.class);
        verify(ackReplyConsumer).ack();
        verify(commandHandler).apply(commandContextArgumentCaptor.capture());
        assertThat(commandContextArgumentCaptor.getValue()).isNotNull();
        assertThat(commandContextArgumentCaptor.getValue().getCommand().isValid()).isTrue();
    }

    /**
     * Verifies that an error response is sent to the application if no command handler can be found for command.
     */
    @Test
    public void testHandleCommandMessageWithNoHandlerForDevice() {
        final PubsubMessage message = getPubSubMessage(subject, "true");
        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);

        internalCommandConsumer.handleCommandMessage(message, ackReplyConsumer);
        verify(ackReplyConsumer).ack();
        verify(commandHandler, never()).apply(any(PubSubBasedCommandContext.class));
        verify(commandResponseSender).sendCommandResponse(
                argThat(t -> t.getTenantId().equals(tenantId)),
                argThat(r -> r.getDeviceId().equals(deviceId)),
                argThat(cr -> cr.getStatus() == HttpURLConnection.HTTP_UNAVAILABLE),
                any());
    }

    /**
     * Verifies that a failed future is returned if an IllegalArgumentException is thrown when no command can be created
     * by the PubSubMessage.
     */
    @Test
    public void testHandleCommandMessageFailedWhenMessageContainsNoAttributes() {
        final PubsubMessage message = PubsubMessage.newBuilder().putAllAttributes(Collections.emptyMap()).build();
        final Function<CommandContext, Future<Void>> commandHandler = mock(Function.class);
        final Context context = VertxMockSupport.mockContext(mock(Vertx.class));
        commandHandlers.putCommandHandler(tenantId, deviceId, null, commandHandler, context);

        final Future<Void> result = internalCommandConsumer.handleCommandMessage(message, ackReplyConsumer);
        assertThat(result.failed()).isTrue();
        verify(ackReplyConsumer).ack();
    }

    private PubsubMessage getPubSubMessage(final String subject, final String responseRequired) {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        attributes.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);
        attributes.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, "my-correlation-id");

        Optional.ofNullable(subject)
                .ifPresent(ok -> attributes.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject));
        Optional.ofNullable(responseRequired)
                .ifPresent(
                        ok -> attributes.put(PubSubMessageHelper.PUBSUB_PROPERTY_RESPONSE_REQUIRED, responseRequired));

        return PubsubMessage.newBuilder().putAllAttributes(attributes).build();
    }

}
