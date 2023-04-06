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
package org.eclipse.hono.client.notification.pubsub;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherClient;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.test.TracingMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link PubSubBasedNotificationSender}.
 */
@ExtendWith(VertxExtension.class)
public class PubSubBasedNotificationSenderTest {

    private static final String PROJECT_ID = "my-project";
    private static final String TENANT_ID = "my-tenant";
    private static final Instant CREATION_TIME = Instant.parse("2023-02-21T10:15:30Z");
    private static final LifecycleChange CHANGE = LifecycleChange.CREATE;
    private Tracer tracer;
    private PubSubPublisherFactory factory;
    private PubSubPublisherClient client;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        final Span span = TracingMockSupport.mockSpan();
        tracer = TracingMockSupport.mockTracer(span);
        factory = mock(PubSubPublisherFactory.class);
        client = mock(PubSubPublisherClient.class);
    }

    /**
     * Verifies that the constructor throws a NullPointerException if a parameter is {@code null}.
     */
    @Test
    public void testThatConstructorThrowsOnMissingParameter() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedNotificationSender(null, PROJECT_ID, tracer)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedNotificationSender(factory, null, tracer)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new PubSubBasedNotificationSender(factory, PROJECT_ID, null)));

    }

    /**
     * Verifies that sending a message failed when sender was not started before.
     */
    @Test
    public void testPublishFailsWhenSenderIsNotStartedBefore() {
        final var notificationSender = new PubSubBasedNotificationSender(factory, PROJECT_ID, tracer);
        final var notification = new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME, false, false);
        final Future<Void> result = notificationSender.publish(notification);
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(ServerErrorException.class);
    }

    /**
     * Verifies that the PubSubClient is added successfully for notification topic and the message is sent successfully.
     */
    @Test
    public void testPublishSucceeds() {
        when(client.publish(any(PubsubMessage.class))).thenReturn(Future.succeededFuture());
        final String topic = String.format("%s.%s", TenantChangeNotification.ADDRESS, "notification");
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        final var notificationSender = new PubSubBasedNotificationSender(factory, PROJECT_ID, tracer);
        final var notification = new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME, false, false);

        notificationSender.start();
        final Future<Void> result = notificationSender.publish(notification);
        assertThat(result.succeeded()).isTrue();
        verify(client).publish(any(PubsubMessage.class));
    }

    /**
     * Verifies that the publish method returns the underlying error wrapped in a {@link ServerErrorException}, if
     * publishing is failing.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testThatPublishFailsWithExpectedError(final VertxTestContext ctx) {
        when(client.publish(any(PubsubMessage.class))).thenReturn(Future.failedFuture("error"));
        final String topic = String.format("%s.%s", TenantChangeNotification.ADDRESS, "notification");
        when(factory.getOrCreatePublisher(topic)).thenReturn(client);

        final var notificationSender = new PubSubBasedNotificationSender(factory, PROJECT_ID, tracer);
        final var notification = new TenantChangeNotification(CHANGE, TENANT_ID, CREATION_TIME, false, false);

        notificationSender.start();
        notificationSender.publish(notification)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(503);
                    });
                    ctx.completeNow();
                }));
        verify(client).publish(any(PubsubMessage.class));
    }

}
