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
package org.eclipse.hono.client.pubsub;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Random;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherClient;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * Verifies the generic behavior of AbstractPubSubBasedMessageSender.
 */
public class AbstractPubSubBasedMessageSenderTest {

    private final String TOPIC_NAME = "event";

    private final String PROJECT_ID = "test-project";

    private final String TENANT_ID = "tenant";

    private final String DEVICE_ID = "device";

    private final Tracer tracer = NoopTracerFactory.create();

    private PubSubPublisherFactory factory;

    private AbstractPubSubBasedMessageSender sender;

    private String topic;

    private String topicWithSubtopic;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        topic = String.format("%s.%s", TENANT_ID, TOPIC_NAME);
        topicWithSubtopic = String.format("%s.%s.%s", TENANT_ID, TOPIC_NAME, "subtopic");
        factory = mock(PubSubPublisherFactory.class);
        sender = new AbstractPubSubBasedMessageSender(factory, TOPIC_NAME, PROJECT_ID, tracer) {
        };
        sender.start();
    }

    /**
     * Verifies that the constructor throws a NullPointerException if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AbstractPubSubBasedMessageSender(null, TOPIC_NAME, PROJECT_ID, tracer) {
                        }),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AbstractPubSubBasedMessageSender(factory, null, PROJECT_ID, tracer) {
                        }),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AbstractPubSubBasedMessageSender(factory, TOPIC_NAME, null, tracer) {
                        }),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AbstractPubSubBasedMessageSender(factory, TOPIC_NAME, PROJECT_ID, null) {
                        }));
    }

    /**
     * Verifies that the send method returns a failed future wrapped in a {@link ServerErrorException}.
     */
    @Test
    public void testSendFailedWhenLifecycleStatusIsNotStarted() {
        sender.stop();

        final var result = sender.sendAndWaitForOutcome(TOPIC_NAME, TENANT_ID, DEVICE_ID, null, Map.of(),
                NoopSpan.INSTANCE);

        assertThat(result.failed()).isTrue();
        result.onFailure(t -> assertThat(t.getClass()).isEqualTo(ServerErrorException.class));
    }

    /**
     * Verifies that the send method returns a failed future.
     */
    @Test
    public void testSendFailed() {
        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any(PubsubMessage.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is null")));
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        final var result = sender.sendAndWaitForOutcome(topic, TENANT_ID, DEVICE_ID, Buffer.buffer("test payload"),
                Map.of(), NoopSpan.INSTANCE);

        assertThat(result.failed()).isTrue();
        result.onFailure(t -> assertThat(t.getClass()).isEqualTo(ServerErrorException.class));
    }

    /**
     * Verifies that the send method returns a failed future on the fallback topic.
     */
    @Test
    public void testSendFailedOnFallbackTopic() {
        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any(PubsubMessage.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is null")));
        when(factory.getOrCreatePublisher(topicWithSubtopic)).thenReturn(client);

        final PubSubPublisherClient fallbackClient = mock(PubSubPublisherClient.class);
        when(fallbackClient.publish(Mockito.any(PubsubMessage.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is null")));
        when(factory.getOrCreatePublisher(topic)).thenReturn(fallbackClient);

        final var result = sender.sendAndWaitForOutcome(topicWithSubtopic, TENANT_ID, DEVICE_ID,
                Buffer.buffer("test payload"),
                Map.of(), NoopSpan.INSTANCE);

        assertThat(result.failed()).isTrue();
        result.onFailure(t -> assertThat(t.getClass()).isEqualTo(ServerErrorException.class));
    }

    /**
     * Verifies that the send method succeeded.
     */
    @Test
    public void testSendSucceeded() {
        final Map<String, Object> properties = getProperties();
        final Map<String, String> attributes = getAttributes();
        final byte[] b = new byte[22];
        new Random().nextBytes(b);
        final ByteString bs = ByteString.copyFrom(b);

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any())).thenReturn(Future.succeededFuture());
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        final var result = sender.sendAndWaitForOutcome(
                topic,
                TENANT_ID,
                DEVICE_ID,
                Buffer.buffer(b),
                properties,
                NoopSpan.INSTANCE);
        assertThat(result.succeeded()).isTrue();
        final var publishedMessage = ArgumentCaptor.forClass(PubsubMessage.class);
        Mockito.verify(client).publish(publishedMessage.capture());
        assertThat(publishedMessage.getValue().getAttributesMap()).containsExactlyEntriesIn(attributes);
        assertThat(publishedMessage.getValue().getOrderingKey()).isEqualTo(DEVICE_ID);
        assertThat(publishedMessage.getValue().getData()).isEqualTo(bs);
    }

    /**
     * Verifies that the send method succeeded on the fallback topic.
     */
    @Test
    public void testSucceededOnFallbackTopic() {
        final byte[] b = new byte[22];
        new Random().nextBytes(b);
        final ByteString bs = ByteString.copyFrom(b);

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any(PubsubMessage.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is null")));
        when(factory.getOrCreatePublisher(topicWithSubtopic)).thenReturn(client);

        final PubSubPublisherClient fallbackClient = mock(PubSubPublisherClient.class);
        when(fallbackClient.publish(Mockito.any(PubsubMessage.class))).thenReturn(Future.succeededFuture());
        when(factory.getOrCreatePublisher(topic)).thenReturn(fallbackClient);

        final var result = sender.sendAndWaitForOutcome(
                topicWithSubtopic,
                TENANT_ID,
                DEVICE_ID,
                Buffer.buffer(b),
                Map.of(),
                NoopSpan.INSTANCE);

        assertThat(result.succeeded()).isTrue();
        final var publishedMessage = ArgumentCaptor.forClass(PubsubMessage.class);
        Mockito.verify(client).publish(publishedMessage.capture());
        assertThat(publishedMessage.getValue().getOrderingKey()).isEqualTo(DEVICE_ID);
        assertThat(publishedMessage.getValue().getData()).isEqualTo(bs);
    }

    private Map<String, Object> getProperties() {
        return Map.of(
                "device-Id", DEVICE_ID,
                "tenant-Id", TENANT_ID,
                "no string", 123);
    }

    private Map<String, String> getAttributes() {
        return Map.of(
                "device-Id", DEVICE_ID,
                "tenant-Id", TENANT_ID,
                "no string", "123");
    }
}
