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

import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherClient;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;

/**
 * Verifies behavior of {@link PubSubBasedCommandResponseSender}.
 */
public class PubSubBasedCommandResponseSenderTest {

    private final String projectId = "test-project";

    private Vertx vertx;
    private Tracer tracer;

    private PubSubPublisherFactory factory;
    private PubSubPublisherClient client;
    private PubSubBasedCommandResponseSender commandResponseSender;

    @BeforeEach
    void setUp() {
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));

        tracer = TracingMockSupport.mockTracer(TracingMockSupport.mockSpan());
        client = mock(PubSubPublisherClient.class);
        factory = mock(PubSubPublisherFactory.class);
    }

    @Test
    void testIfValidCommandResponseIsSent() {
        final String correlationId = "my-correlation-id";
        final String deviceId = "test-device";
        final String tenantId = "test-tenant";
        final String contentType = "text/plain";
        final String payload = "test-payload";
        final String topic = String.format("%s.%s", tenantId, CommandConstants.COMMAND_RESPONSE_ENDPOINT);
        final int status = 200;

        final CommandResponse commandResponse = CommandResponse.fromAddressAndCorrelationId(
                String.format("%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId,
                        Commands.getDeviceFacingReplyToId("", deviceId, MessagingType.pubsub)),
                correlationId,
                Buffer.buffer(payload),
                contentType,
                status);

        final TenantObject tenantObject = TenantObject.from(tenantId);
        tenantObject.setResourceLimits(new ResourceLimits().setMaxTtlCommandResponse(10L));

        when(client.publish(any(PubsubMessage.class))).thenReturn(Future.succeededFuture());
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);
        commandResponseSender = new PubSubBasedCommandResponseSender(vertx, factory, projectId, tracer);
        commandResponseSender.start();

        final Future<Void> result = commandResponseSender.sendCommandResponse(
                tenantObject,
                new RegistrationAssertion(deviceId),
                commandResponse,
                NoopSpan.INSTANCE.context());

        assertThat(result.succeeded()).isTrue();
        verify(client).publish(any(PubsubMessage.class));
    }
}
