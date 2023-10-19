/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.kafka.client.producer.KafkaProducer;


/**
 * Verifies generic behavior of {@link AbstractKafkaBasedMessageSender}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
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
        return CachingKafkaProducerFactory.testFactory(
                vertxMock,
                (n, c) -> KafkaClientUnitTestHelper.newKafkaProducer(mockProducer));
    }

    private AbstractKafkaBasedMessageSender<Buffer> newSender(final KafkaProducerFactory<String, Buffer> factory) {
        return new AbstractKafkaBasedMessageSender<>(factory, PRODUCER_NAME, config, tracer) {
        };
    }

    /**
     * Verifies that on start up a producer is created which is closed on shut down.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testLifecycle(final VertxTestContext ctx) {

        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(true);
        final var factory = newProducerFactory(mockProducer);
        final var sender = newSender(factory);
        final Promise<Void> readyTracker = Promise.promise();
        sender.addOnKafkaProducerReadyHandler(readyTracker);

        assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isTrue();
        sender.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> {
                ctx.verify(() -> assertThat(factory.getProducer(PRODUCER_NAME).isPresent())
                        .isTrue());
                return sender.stop();
            })
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> assertThat(factory.getProducer(PRODUCER_NAME).isPresent()).isFalse());
                ctx.completeNow();
            }));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStartSucceedsEvenIfProducerIsNotReadyYet(final VertxTestContext ctx) {

        final Promise<KafkaProducer<String, Buffer>> producer = Promise.promise();
        final KafkaProducerFactory<String, Buffer> factory = mock(KafkaProducerFactory.class);
        when(factory.getOrCreateProducerWithRetries(
                anyString(),
                any(KafkaProducerConfigProperties.class),
                any(BooleanSupplier.class),
                any(Duration.class)))
            .thenReturn(producer.future());
        final var sender = newSender(factory);
        final Promise<Void> readyTracker = Promise.promise();
        sender.addOnKafkaProducerReadyHandler(readyTracker);

        sender.start()
            .compose(ok -> {
                ctx.verify(() -> assertThat(readyTracker.future().isComplete()).isFalse());
                // WHEN the creation of the producer finally succeeds
                producer.complete(mock(KafkaProducer.class));
                return readyTracker.future();
            })
            .onComplete(ctx.succeedingThenComplete());

    }

    private void testSendFails(
            final RuntimeException sendError,
            final int expectedErrorCode,
            final boolean expectProducerToBeClosed) {

        final VertxTestContext ctx = new VertxTestContext();
        // GIVEN a sender
        final var mockProducer = KafkaClientUnitTestHelper.newMockProducer(false);
        final var factory = newProducerFactory(mockProducer);
        final var sender = newSender(factory);

        // WHEN sending a message fails
        final var result = sender.sendAndWaitForOutcome("topic", "tenant", "device", null, Map.of(), NoopSpan.INSTANCE);
        mockProducer.errorNext(sendError);

        result.onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN client sees the expected error
                assertThat(t).isInstanceOf(ServerErrorException.class);
                assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(expectedErrorCode);
                assertThat(t.getCause()).isEqualTo(sendError);
                assertThat(factory.getProducer(PRODUCER_NAME).isEmpty()).isEqualTo(expectProducerToBeClosed);
                assertThat(mockProducer.closed()).isEqualTo(expectProducerToBeClosed);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the send method returns the underlying error wrapped in a {@link ServerErrorException}.
     */
    @Test
    public void testSendFailsWithTheExpectedError() {

        testSendFails(new RuntimeException("boom"), HttpURLConnection.HTTP_UNAVAILABLE, false);
    }

    /**
     * Verifies that the producer is closed when sending of a message fails with a fatal error.
     */
    @Test
    public void testProducerIsClosedOnFatalError() {

        testSendFails(new AuthorizationException("go away"), HttpURLConnection.HTTP_UNAVAILABLE, true);
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
        final Promise<Void> readyTracker = Promise.promise();
        final var sender = newSender(factory);
        sender.addOnKafkaProducerReadyHandler(readyTracker);
        sender.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> sender.sendAndWaitForOutcome("topic", "tenant", "device", null, Map.of(), NoopSpan.INSTANCE))
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
        final Promise<Void> readyTracker = Promise.promise();
        final var sender = newSender(factory);
        sender.addOnKafkaProducerReadyHandler(readyTracker);
        sender.start()
            .compose(ok -> readyTracker.future())
            .compose(ok -> sender.sendAndWaitForOutcome("topic", "tenant", "device", null, properties, NoopSpan.INSTANCE))
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
