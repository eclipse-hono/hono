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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherClient;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Tracer;
import io.vertx.core.Future;

/**
 * Verifies behavior of {@link PubSubBasedInternalCommandSender}.
 */
public class PubSubBasedInternalCommandSenderTest {

    private final String adapterInstanceId = "test-adapter";

    private final String deviceId = "test-device";

    private final String tenantId = "test-tenant";

    private PubSubPublisherFactory factory;

    private PubSubBasedInternalCommandSender internalCommandSender;

    @BeforeEach
    void setUp() {
        factory = mock(PubSubPublisherFactory.class);
        final Tracer tracer = TracingMockSupport.mockTracer(TracingMockSupport.mockSpan());
        final String projectId = "test-project";
        internalCommandSender = new PubSubBasedInternalCommandSender(factory, projectId, tracer);
    }

    @Test
    void testThatErrorIsThrownWhenCommandIsNotPubSubBasedCommand() {
        final Command command = mock(Command.class);
        final CommandContext commandContext = mock(CommandContext.class);
        when(commandContext.getCommand()).thenReturn(command);

        assertThrows(IllegalArgumentException.class,
                () -> internalCommandSender.sendCommand(commandContext, adapterInstanceId));
    }

    @Test
    void testThatCommandIsSentSuccessfully() {
        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(any(PubsubMessage.class))).thenReturn(Future.succeededFuture());

        final String topic = String.format("%s.%s", adapterInstanceId, CommandConstants.INTERNAL_COMMAND_ENDPOINT);
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        final PubsubMessage message = getPubSubMessage();
        final PubSubBasedCommand command = PubSubBasedCommand.from(message, tenantId);
        final CommandContext commandContext = mock(CommandContext.class);
        when(commandContext.getCommand()).thenReturn(command);

        internalCommandSender.start();
        final Future<Void> result = internalCommandSender.sendCommand(commandContext, adapterInstanceId);

        assertThat(result.succeeded()).isTrue();
        verify(commandContext).accept();
        verify(client).publish(any(PubsubMessage.class));
    }

    private PubsubMessage getPubSubMessage() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        attributes.put(MessageHelper.APP_PROPERTY_TENANT_ID, tenantId);

        return PubsubMessage.newBuilder().putAllAttributes(attributes).build();
    }

}
