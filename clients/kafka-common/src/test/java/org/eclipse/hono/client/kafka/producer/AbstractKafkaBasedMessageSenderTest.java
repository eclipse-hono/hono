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


package org.eclipse.hono.client.kafka.producer;

import static org.mockito.Mockito.mock;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.header.Headers;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.kafka.test.KafkaClientUnitTestHelper;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Verifies generic behavior of {@link AbstractKafkaBasedMessageSender}.
 *
 */
@ExtendWith(VertxExtension.class)
public class AbstractKafkaBasedMessageSenderTest {

    private static final String PRODUCER_NAME = "test-producer";

    private final Tracer tracer = NoopTracerFactory.create();
    private final Vertx vertxMock = mock(Vertx.class);
    private MessagingKafkaProducerConfigProperties config;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        config = new MessagingKafkaProducerConfigProperties();
        config.setProducerConfig(Map.of("hono.kafka.producerConfig.bootstrap.servers", "localhost:9092"));
    }

    private CachingKafkaProducerFactory<String, Buffer> newProducerFactory(
            final MockProducer<String, Buffer> mockProducer) {
        return CachingKafkaProducerFactory
                .testFactory(vertxMock, (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    private AbstractKafkaBasedMessageSender newSender(final MockProducer<String, Buffer> mockProducer) {
        return newSender(newProducerFactory(mockProducer));
    }

    private AbstractKafkaBasedMessageSender newSender(final KafkaProducerFactory<String, Buffer> factory) {
        return new AbstractKafkaBasedMessageSender(factory, PRODUCER_NAME, config, tracer) {
        };
    }

    /**
     * Verifies that on start up a producer is created which is closed on shut down.
     */
    @Test
    public void testLifecycle() {
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = newProducerFactory(mockProducer);
        final var sender = newSender(factory);

        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
        sender.start();
        assertThat(factory.getProducer(PRODUCER_NAME).isPresent()).isTrue();
        sender.stop();
        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
    }

    /**
     * Verifies that the send method returns the underlying error wrapped in a {@link ServerErrorException}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSendFailsWithTheExpectedError(final VertxTestContext ctx) {

        // GIVEN a sender sending a message
        final RuntimeException expectedError = new RuntimeException("boom");
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final var sender = newSender(mockProducer);

        sender.sendAndWaitForOutcome("topic", "tenant", "device", null, Map.of(), NoopSpan.INSTANCE)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN it fails with the expected error
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(503);
                        assertThat(t.getCause()).isEqualTo(expectedError);
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);
    }

    /**
     * Verifies that the producer is closed when sending of a message fails with a fatal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerIsClosedOnFatalError(final VertxTestContext ctx) {

        final AuthorizationException expectedError = new AuthorizationException("go away");

        // GIVEN a sender sending a message
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final var factory = newProducerFactory(mockProducer);
        newSender(factory).sendAndWaitForOutcome("topic", "tenant", "device", null, Map.of(), NoopSpan.INSTANCE)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the producer is removed and closed
                        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
                        assertThat(mockProducer.closed()).isTrue();
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);
    }

    /**
     * Verifies that the producer is not closed when sending of a message fails with a non-fatal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProducerIsNotClosedOnNonFatalError(final VertxTestContext ctx) {

        final RuntimeException expectedError = new RuntimeException("foo");

        // GIVEN a sender sending a message
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final var factory = newProducerFactory(mockProducer);
        newSender(factory).sendAndWaitForOutcome("topic", "tenant", "device", null, Map.of(), NoopSpan.INSTANCE)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the producer is present and still open
                        assertThat(factory.getProducer(PRODUCER_NAME).isPresent()).isTrue();
                        assertThat(mockProducer.closed()).isFalse();
                    });
                    ctx.completeNow();
                }));

        // WHEN the send operation fails
        mockProducer.errorNext(expectedError);
    }

    /**
     * Verifies that a <em>creation-time</em> header is added to messages that do not contain one already.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsAddedWhenNotSet(final VertxTestContext ctx) {

        // WHEN sending a message with no properties
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = newProducerFactory(mockProducer);

        newSender(factory).sendAndWaitForOutcome("topic", "tenant", "device", null, Map.of(), NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(t -> {
                ctx.verify(() -> {
                    final Headers headers = mockProducer.history().get(0).headers();
                    // THEN the producer record contains a creation time
                    assertThat(headers.headers(MessageHelper.SYS_PROPERTY_CREATION_TIME)).isNotNull();
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that if the properties contain a <em>creation-time</em> property then it is preserved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatCreationTimeIsNotChanged(final VertxTestContext ctx) {

        // GIVEN properties that contain creation-time
        final long creationTime = 12345L;
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.SYS_PROPERTY_CREATION_TIME, creationTime);

        // WHEN sending the message
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = newProducerFactory(mockProducer);

        newSender(factory).sendAndWaitForOutcome("topic", "tenant", "device", null, properties, NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(t -> {
                ctx.verify(() -> {
                    // THEN the creation time is preserved
                    KafkaClientUnitTestHelper.assertUniqueHeaderWithExpectedValue(
                            mockProducer.history().get(0).headers(),
                            MessageHelper.SYS_PROPERTY_CREATION_TIME,
                            creationTime);
                });
                ctx.completeNow();
            }));
    }
}
