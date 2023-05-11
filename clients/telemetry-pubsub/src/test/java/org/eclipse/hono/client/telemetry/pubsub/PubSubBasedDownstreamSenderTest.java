/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.telemetry.pubsub;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherClient;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;

/**
 * Verifies the behavior of {@link PubSubBasedDownstreamSender}.
 */
public class PubSubBasedDownstreamSenderTest {

    private final String EVENT_TOPIC = "event";

    private final String TELEMETRY_TOPIC = "telemetry";

    private final String PROJECT_ID = "project";

    private final String contentType = "text/plain";

    private final String payload = "the payload";

    private final TenantObject tenant = new TenantObject("test-tenant", true);

    private final RegistrationAssertion device = new RegistrationAssertion("test-device");

    private Vertx vertx;

    private Span span;

    private Tracer tracer;

    private PubSubPublisherFactory factory;
    private PubSubPublisherClient client;
    private PubSubBasedDownstreamSender sender;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));

        span = TracingMockSupport.mockSpan();
        tracer = TracingMockSupport.mockTracer(span);
        client = mock(PubSubPublisherClient.class);
        factory = mock(PubSubPublisherFactory.class);
    }

    /**
     * Verifies that the constructor throws a NullPointerException if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedDownstreamSender(null, factory, EVENT_TOPIC, PROJECT_ID, true, tracer)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedDownstreamSender(vertx, null, EVENT_TOPIC, PROJECT_ID, true, tracer)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedDownstreamSender(vertx, factory, null, PROJECT_ID, true, tracer)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedDownstreamSender(vertx, factory, EVENT_TOPIC, null, true, tracer)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedDownstreamSender(vertx, factory, EVENT_TOPIC, PROJECT_ID, true, null)));
    }

    /**
     * Verifies that sending a message failed when sender was not started before.
     */
    @Test
    public void testSendEventFailedWhenSenderIsNotStartedBefore() {
        sender = new PubSubBasedDownstreamSender(vertx, factory, EVENT_TOPIC, PROJECT_ID, true, tracer);
        final Map<String, Object> properties = Map.of(
                "foo", "bar",
                MessageHelper.SYS_HEADER_PROPERTY_TTL, 5);

        final Future<Void> result = sender.sendEvent(
                tenant,
                device,
                contentType,
                Buffer.buffer(payload),
                properties,
                null);
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(ServerErrorException.class);
    }

    /**
     * Verifies that the PubSubClient is added successfully for topic Event and the message is sent successfully.
     */
    @Test
    public void testSendEventSucceeds() {
        sender = new PubSubBasedDownstreamSender(vertx, factory, EVENT_TOPIC, PROJECT_ID, true, tracer);

        final Map<String, Object> properties = Map.of(
                "foo", "bar",
                MessageHelper.SYS_HEADER_PROPERTY_TTL, 5);

        when(client.publish(any(PubsubMessage.class))).thenReturn(Future.succeededFuture());
        final String topic = String.format("%s.%s", "test-tenant", EVENT_TOPIC);
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        sender.start();
        final Future<Void> result = sender.sendEvent(
                tenant,
                device,
                contentType,
                Buffer.buffer(payload),
                properties,
                null);
        assertThat(result.succeeded()).isTrue();
        verify(span).finish();
        verify(client).publish(any(PubsubMessage.class));
    }

    /**
     * Verifies that the PubSubClient is added successfully for topic Event including subtopics and the message is sent
     * successfully.
     */
    @Test
    public void testSendEventWithSubtopicsSucceeds() {
        sender = new PubSubBasedDownstreamSender(vertx, factory, EVENT_TOPIC, PROJECT_ID, true, tracer);

        final Map<String, Object> properties = Map.of(
                "foo", "bar",
                "orig_address",
                String.format("%s/%s/%s/%s/%s/%s", EVENT_TOPIC, "test-tenant", "test-device", "subtopic1", "subtopic2",
                        "?metadata=true"),
                MessageHelper.SYS_HEADER_PROPERTY_TTL, 5);

        when(client.publish(any(PubsubMessage.class))).thenReturn(Future.succeededFuture());
        final String topic = String.format("%s.%s.%s.%s", "test-tenant", EVENT_TOPIC, "subtopic1", "subtopic2");
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        sender.start();
        final Future<Void> result = sender.sendEvent(
                tenant,
                device,
                contentType,
                Buffer.buffer(payload),
                properties,
                null);
        assertThat(result.succeeded()).isTrue();
        verify(span).finish();
        verify(client).publish(any(PubsubMessage.class));
    }

    /**
     * Verifies that the PubSubClient is added successfully for topic Telemetry and the message is sent successfully.
     */
    @Test
    public void testSendTelemetrySucceeds() {
        sender = new PubSubBasedDownstreamSender(vertx, factory, TELEMETRY_TOPIC, PROJECT_ID, true, tracer);

        final Map<String, Object> properties = Map.of(
                "foo", "bar",
                MessageHelper.SYS_HEADER_PROPERTY_TTL, 5);

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any())).thenReturn(Future.succeededFuture());
        final String topic = String.format("%s.%s", "test-tenant", TELEMETRY_TOPIC);
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        sender.start();
        final Future<Void> result = sender.sendTelemetry(
                tenant,
                device,
                QoS.AT_LEAST_ONCE,
                contentType,
                Buffer.buffer(payload),
                properties,
                null);
        assertThat(result.succeeded()).isTrue();
        verify(span).finish();
        verify(client).publish(any(PubsubMessage.class));
    }

    /**
     * Verifies that the PubSubClient is added successfully for topic Telemetry including subtopics and the message is
     * sent successfully.
     */
    @Test
    public void testSendTelemetryWithSubtopicsSucceeds() {
        sender = new PubSubBasedDownstreamSender(vertx, factory, TELEMETRY_TOPIC, PROJECT_ID, true, tracer);

        final Map<String, Object> properties = Map.of(
                "foo", "bar",
                "orig_address",
                String.format("%s/%s/%s/%s/%s", TELEMETRY_TOPIC, "test-tenant", "test-device", "subtopic1",
                        "subtopic2"),
                MessageHelper.SYS_HEADER_PROPERTY_TTL, 5);

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any())).thenReturn(Future.succeededFuture());
        final String topic = String.format("%s.%s.%s.%s", "test-tenant", TELEMETRY_TOPIC, "subtopic1", "subtopic2");
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        sender.start();
        final Future<Void> result = sender.sendTelemetry(
                tenant,
                device,
                QoS.AT_LEAST_ONCE,
                contentType,
                Buffer.buffer(payload),
                properties,
                null);
        assertThat(result.succeeded()).isTrue();
        verify(span).finish();
        verify(client).publish(any(PubsubMessage.class));
    }
}
