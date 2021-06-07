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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        final var registrationClient = mock(DeviceRegistrationClient.class);
        final var tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0)));
        });
        final var deviceConnectionInfo = mock(DeviceConnectionInfo.class);
        commandConsumerFactory = mock(CommandConsumerFactory.class);
        final MessagingClient<CommandConsumerFactory> factories = new MessagingClient<>();
        factories.setClient(MessagingType.amqp, commandConsumerFactory);
        final Vertx vertx = mock(Vertx.class);
        context = VertxMockSupport.mockContext(vertx);
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
}
