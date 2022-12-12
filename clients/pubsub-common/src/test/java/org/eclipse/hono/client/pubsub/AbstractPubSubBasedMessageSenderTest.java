/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.LifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the generic behavior of {@link AbstractPubSubBasedMessageSender}.
 */
public class AbstractPubSubBasedMessageSenderTest {

    private final String TOPIC = "event";

    private final String PROJECT_ID = "test-project";

    private final String TENANT_ID = "tenant";

    private final String DEVICE_ID = "device";

    private final Tracer tracer = NoopTracerFactory.create();

    private LifecycleStatus lifecycleStatus;

    private CachingPubSubPublisherFactory factory;

    private AbstractPubSubBasedMessageSender sender;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        factory = mock(CachingPubSubPublisherFactory.class);
        lifecycleStatus = mock(LifecycleStatus.class);
        sender = new AbstractPubSubBasedMessageSender(factory, TOPIC, PROJECT_ID, tracer, lifecycleStatus) {
        };
    }

    /**
     * Verifies that the constructor throws a NullPointerException if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        assertThrows(NullPointerException.class,
                () -> new AbstractPubSubBasedMessageSender(null, TOPIC, PROJECT_ID, tracer) {

                });

        assertThrows(NullPointerException.class,
                () -> new AbstractPubSubBasedMessageSender(factory, null, PROJECT_ID, tracer) {

                });

        assertThrows(NullPointerException.class,
                () -> new AbstractPubSubBasedMessageSender(factory, TOPIC, null, tracer) {

                });

        assertThrows(NullPointerException.class,
                () -> new AbstractPubSubBasedMessageSender(factory, TOPIC, PROJECT_ID, null) {

                });
    }

    /**
     * Verifies that start up succeeded lifecycleStatus is set correctly.
     */
    @Test
    public void testStartSucceedsWhenLifecycleStatusIsStartingOrStarted() {
        when(lifecycleStatus.isStarting()).thenReturn(true);
        assertThat(sender.start().succeeded()).isTrue();

        when(lifecycleStatus.setStarting()).thenReturn(true);
        assertThat(sender.start().succeeded()).isTrue();
    }

    /**
     * Verifies that start up failed when component is already started and not in state stopped.
     */
    @Test
    public void testStartFailedWhenLifecycleStatusIsNotInStateStopped() {
        when(lifecycleStatus.setStarting()).thenReturn(false);
        assertThat(sender.start().failed()).isTrue();
    }

    /**
     * Verifies that the send method returns a failed future wrapped in a {@link ServerErrorException}.
     */
    @Test
    public void testSendFailedWhenLifecycleStatusIsNotStarted() {
        final VertxTestContext ctx = new VertxTestContext();
        when(lifecycleStatus.isStarted()).thenReturn(false);

        final var result = sender.sendAndWaitForOutcome(TOPIC, TENANT_ID, DEVICE_ID, null, Map.of(), NoopSpan.INSTANCE);

        assertThat(result.failed()).isTrue();
        result.onFailure(t -> assertThat(t.getMessage()).isEqualTo("sender not started"));
    }

    /**
     * Verifies that the send method throws a {@link ServerErrorException}.
     */
    @Test
    public void testSendFailed() {
        when(lifecycleStatus.isStarted()).thenReturn(true);

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_CONFLICT, "client is null")));
        when(factory.getOrCreatePublisher(TOPIC, PROJECT_ID, TENANT_ID)).thenReturn(client);

        try (MockedStatic<PubsubMessage> pubSubMessage = Mockito.mockStatic(PubsubMessage.class)) {
            final PubsubMessage.Builder builder = mock(PubsubMessage.Builder.class);
            when(builder.putAllAttributes(Mockito.any())).thenReturn(builder);
            when(builder.setOrderingKey(DEVICE_ID)).thenReturn(builder);
            when(builder.setData(Mockito.any())).thenReturn(builder);

            final PubsubMessage message = mock(PubsubMessage.class);
            when(builder.build()).thenReturn(message);

            pubSubMessage.when(PubsubMessage::newBuilder).thenReturn(builder);

            assertThrows(NullPointerException.class,
                    () -> sender.sendAndWaitForOutcome(TOPIC, TENANT_ID, DEVICE_ID, null, Map.of(), NoopSpan.INSTANCE));

            assertThrows(ServerErrorException.class,
                    () -> sender.sendAndWaitForOutcome(TOPIC, TENANT_ID, DEVICE_ID, Buffer.buffer("test payload"),
                            Map.of(), NoopSpan.INSTANCE));
        }

    }

    /**
     * Verifies that the send method succeeded.
     */
    @Test
    public void testSendSucceeded() {
        final Map<String, Object> properties = getProperties();
        final Map<String, String> attributes = getAttributes();

        when(lifecycleStatus.isStarted()).thenReturn(true);

        final PubSubPublisherClient client = mock(PubSubPublisherClient.class);
        when(client.publish(Mockito.any())).thenReturn(Future.succeededFuture());
        when(factory.getOrCreatePublisher(TOPIC, PROJECT_ID, TENANT_ID)).thenReturn(client);

        try (MockedStatic<PubsubMessage> pubSubMessage = Mockito.mockStatic(PubsubMessage.class)) {
            final PubsubMessage.Builder builder = mock(PubsubMessage.Builder.class);
            when(builder.putAllAttributes(attributes)).thenReturn(builder);
            when(builder.setOrderingKey(DEVICE_ID)).thenReturn(builder);
            final byte[] b = new byte[22];
            new Random().nextBytes(b);
            final ByteString bs = ByteString.copyFrom(b);
            when(builder.setData(bs)).thenReturn(builder);

            final PubsubMessage message = mock(PubsubMessage.class);
            when(builder.build()).thenReturn(message);

            pubSubMessage.when(PubsubMessage::newBuilder).thenReturn(builder);

            final var result = sender.sendAndWaitForOutcome(TOPIC, TENANT_ID, DEVICE_ID,
                    Buffer.buffer(b),
                    properties,
                    NoopSpan.INSTANCE);
            assertThat(result.succeeded()).isTrue();
        }
    }

    private Map<String, Object> getProperties() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("device-Id", DEVICE_ID);
        properties.put("tenant-Id", TENANT_ID);
        properties.put("no string", 123);
        return properties;
    }

    private Map<String, String> getAttributes() {
        final Map<String, String> attributes = new HashMap<>();
        attributes.put("device-Id", DEVICE_ID);
        attributes.put("tenant-Id", TENANT_ID);
        attributes.put("no string", "123");
        return attributes;
    }

}
