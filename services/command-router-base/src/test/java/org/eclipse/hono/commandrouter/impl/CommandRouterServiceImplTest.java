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


package org.eclipse.hono.commandrouter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterServiceConfigProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;


/**
 * Tests verifying behavior of {@link CommandRouterServiceImpl}.
 *
 */
class CommandRouterServiceImplTest {

    private CommandRouterServiceImpl service;
    private CommandConsumerFactory commandConsumerFactory;
    private Context context;
    private Vertx vertx;
    private TenantClient tenantClient;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        final var registrationClient = mock(DeviceRegistrationClient.class);
        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0)));
        });
        final var deviceConnectionInfo = mock(DeviceConnectionInfo.class);
        commandConsumerFactory = mock(CommandConsumerFactory.class);
        final MessagingClient<CommandConsumerFactory> factories = new MessagingClient<>();
        factories.setClient(MessagingType.amqp, commandConsumerFactory);
        vertx = mock(Vertx.class);
        context = VertxMockSupport.mockContext(vertx);
        when(context.owner()).thenReturn(vertx);
        service = new CommandRouterServiceImpl(
                new CommandRouterServiceConfigProperties(),
                registrationClient,
                tenantClient,
                deviceConnectionInfo,
                factories,
                NoopTracerFactory.create());
        service.setContext(context);
    }

    @Test
    void testEnableCommandRoutingCreatesCommandConsumers() {

        final Deque<Handler<Void>> eventLoop = new LinkedList<>();
        doAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(0));
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());

        final List<String> firstTenants = List.of("tenant1", "tenant2", "tenant3");
        final List<String> secondTenants = List.of("tenant3", "tenant4");
        // WHEN submitting the first list of tenants to enable
        service.enableCommandRouting(firstTenants, NoopSpan.INSTANCE);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        assertThat(eventLoop).hasSize(1);
        // WHEN submitting the second list of tenants
        service.enableCommandRouting(secondTenants, NoopSpan.INSTANCE);
        // still no command consumer has been created
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the first task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer is being created
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant1"), any());
        // AND a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the first task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer is being created
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant2"), any());
        // AND a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the first task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the first task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a single command consumer is being created for the duplicate tenant3 ID
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant3"), any());
        // AND a new task has been added to the event loop
        assertThat(eventLoop).hasSize(1);
        // WHEN running the first task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer is being created
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant4"), any());
        // AND no new task has been added to the event loop
        assertThat(eventLoop).isEmpty();
    }

    /**
     * Verifies that exponential back-off is used for rescheduling attempts
     * to enable command routing if an attempt fails.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testEnableCommandRoutingUsesExponentialBackoff() {

        final Deque<Handler<?>> eventLoop = new LinkedList<>();
        doAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(0));
            return null;
        }).when(context).runOnContext(VertxMockSupport.anyHandler());

        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            eventLoop.addLast(invocation.getArgument(1));
            return 1L;
        });

        when(tenantClient.get(eq("tenant1"), any())).thenReturn(
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)),
                Future.succeededFuture(TenantObject.from("tenant1")));

        final List<String> firstTenants = List.of("tenant1");
        service.enableCommandRouting(firstTenants, NoopSpan.INSTANCE);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // BUT a first task to process the first tenant ID has been scheduled
        verify(context).runOnContext(VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts tha have been made
        verify(vertx).setTimer(eq(400L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts tha have been made
        verify(vertx).setTimer(eq(800L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts tha have been made
        verify(vertx).setTimer(eq(1600L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts tha have been made
        verify(vertx).setTimer(eq(3200L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts tha have been made
        verify(vertx).setTimer(eq(6400L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN no command consumer has been created yet
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), any());
        // AND a new task for retrying the attempt has been scheduled with the
        // delay corresponding to the number of unsuccessful attempts tha have been made
        verify(vertx).setTimer(eq(10000L), VertxMockSupport.anyHandler());
        assertThat(eventLoop).hasSize(1);

        // WHEN running the next task on the event loop
        eventLoop.pollFirst().handle(null);
        // THEN a command consumer has been created
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant1"), any());
        // AND no new task for retrying the attempt has been added to the event loop
        assertThat(eventLoop).hasSize(0);
    }
}
