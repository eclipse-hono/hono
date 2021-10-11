/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.cli.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.eclipse.hono.application.client.ApplicationClient;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.AmqpApplicationClient;
import org.eclipse.hono.application.client.kafka.KafkaApplicationClient;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying the behavior of {@code Receiver}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ReceiverTest {

    private static final String PARAMETERIZED_TEST_NAME_PATTERN = "{displayName} [{index}]; parameters: {argumentsWithNames}";

    private Receiver receiver;

    /**
     * Sets up the receiver with mocks.
     *
     */
    @BeforeEach
    public void setup() {

        final Vertx vertx = mock(Vertx.class);
        when(vertx.getOrCreateContext()).thenReturn(mock(Context.class));

        receiver = new Receiver();
        receiver.setVertx(vertx);
        receiver.tenantId = "TEST_TENANT";
    }

    /**
     * Verifies that the receiver is started successfully with message.type=telemetry.
     *
     * @param applicationClient The application client to use.
     * @param context The vert.x test context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("clientFactoryVariants")
    public void testTelemetryStart(final ApplicationClient<? extends MessageContext> applicationClient,
            final VertxTestContext context) {
        receiver.setApplicationClient(applicationClient);
        receiver.messageType = "telemetry";

        receiver.start().onComplete(
                context.succeeding(result -> {
                   context.verify(() -> {
                       assertNotNull(result.list());
                       assertEquals(1, result.size());
                   });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the receiver is started successfully with message.type=event.
     *
     * @param applicationClient The application client to use.
     * @param context The vert.x test context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("clientFactoryVariants")
    public void testEventStart(final ApplicationClient<? extends MessageContext> applicationClient,
            final VertxTestContext context) {
        receiver.setApplicationClient(applicationClient);
        receiver.messageType = "event";
        receiver.start().onComplete(
                context.succeeding(result -> {
                    context.verify(() -> {
                        assertNotNull(result.list());
                        assertEquals(1, result.size());
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the receiver is started successfully with message.type=all.
     *
     * @param applicationClient The application client to use.
     * @param context The vert.x test context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("clientFactoryVariants")
    public void testDefaultStart(final ApplicationClient<? extends MessageContext> applicationClient,
            final VertxTestContext context) {
        receiver.setApplicationClient(applicationClient);
        receiver.messageType = "all";

        receiver.start().onComplete(
                context.succeeding(result -> {
                    context.verify(() -> {
                        assertNotNull(result.list());
                        assertEquals(2, result.size());
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the receiver fails to start when invalid value is passed to message.type.
     *
     * @param applicationClient The application client to use.
     * @param context The vert.x test context.
     */
    @ParameterizedTest(name = PARAMETERIZED_TEST_NAME_PATTERN)
    @MethodSource("clientFactoryVariants")
    public void testInvalidTypeStart(final ApplicationClient<? extends MessageContext> applicationClient,
            final VertxTestContext context) {
        receiver.setApplicationClient(applicationClient);
        receiver.messageType = "xxxxx";
        receiver.start().onComplete(
                context.failing(result -> context.completeNow()));
    }

    private static Stream<ApplicationClient<? extends MessageContext>> clientFactoryVariants() {

        final AmqpApplicationClient amqpApplicationClientFactory = mock(AmqpApplicationClient.class);
        when(amqpApplicationClientFactory.connect()).thenReturn(Future.succeededFuture());
        when(amqpApplicationClientFactory.createTelemetryConsumer(anyString(), VertxMockSupport.anyHandler(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        when(amqpApplicationClientFactory.createEventConsumer(anyString(), VertxMockSupport.anyHandler(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));

        final KafkaApplicationClient kafkaApplicationClientFactory = mock(KafkaApplicationClient.class);
        when(kafkaApplicationClientFactory.createTelemetryConsumer(anyString(), VertxMockSupport.anyHandler(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        when(kafkaApplicationClientFactory.createEventConsumer(anyString(), VertxMockSupport.anyHandler(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));

        return Stream.of(
                amqpApplicationClientFactory,
                kafkaApplicationClientFactory);
    }
}
