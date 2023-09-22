/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.ReconnectListener;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Tests verifying behavior of {@link ProtocolAdapterCommandConsumerFactoryImpl}.
 *
 */
public class ProtocolAdapterCommandConsumerFactoryTest {

    private CommandRouterClient commandRouterClient;
    private KubernetesContainerInfoProvider kubernetesContainerInfoProvider;
    private BiFunction<String, CommandHandlers, InternalCommandConsumer> internalCommandConsumerSupplier;
    private ProtocolAdapterCommandConsumerFactoryImpl factory;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        commandRouterClient = mock(CommandRouterClient.class, withSettings().extraInterfaces(ConnectionLifecycle.class));
        when(commandRouterClient.registerCommandConsumer(anyString(), anyString(), anyBoolean(), anyString(), any(Duration.class), any()))
            .thenReturn(Future.succeededFuture());
        kubernetesContainerInfoProvider = mock(KubernetesContainerInfoProvider.class);
        when(kubernetesContainerInfoProvider.getContainerId(any())).thenReturn(Future.succeededFuture(null));
        final Vertx vertx = mock(Vertx.class);
        VertxMockSupport.executeBlockingCodeImmediately(vertx);
        factory = new ProtocolAdapterCommandConsumerFactoryImpl(vertx, commandRouterClient, "test-adapter");
        factory.setKubernetesContainerInfoProvider(kubernetesContainerInfoProvider);
        internalCommandConsumerSupplier = mock(BiFunction.class);
        final InternalCommandConsumer internalCommandConsumer = mock(InternalCommandConsumer.class);
        when(internalCommandConsumer.start()).thenReturn(Future.succeededFuture());
        when(internalCommandConsumerSupplier.apply(anyString(), any())).thenReturn(internalCommandConsumer);
        factory.registerInternalCommandConsumer(internalCommandConsumerSupplier);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFactoryReenablesCommandRouting() {

        // GIVEN a consumer factory with command consumers registered for 3 tenants
        final ConnectionLifecycle<HonoConnection> conLifecycle = (ConnectionLifecycle<HonoConnection>) commandRouterClient;
        final ArgumentCaptor<ReconnectListener<HonoConnection>> reconnectListener = ArgumentCaptor.forClass(ReconnectListener.class);
        verify(conLifecycle).addReconnectListener(reconnectListener.capture());
        factory.setMaxTenantIdsPerRequest(2);
        factory.start();

        factory.createCommandConsumer("tenant1", "device1", "adapter", true, ctx -> Future.succeededFuture(), Duration.ofMinutes(10), null);
        factory.createCommandConsumer("tenant2", "device2", "adapter", true, ctx -> Future.succeededFuture(), Duration.ofMinutes(10), null);
        factory.createCommandConsumer("tenant3", "device3", "adapter", true, ctx -> Future.succeededFuture(), Duration.ofMinutes(10), null);

        // WHEN connection is lost and re-established
        final List<String> enabledTenants = new ArrayList<>();
        when(commandRouterClient.enableCommandRouting(anyList(), any())).thenAnswer(invocation -> {
            enabledTenants.addAll(invocation.getArgument(0));
            return Future.succeededFuture();
        });
        reconnectListener.getValue().onReconnect(mock(HonoConnection.class));

        // THEN
        verify(commandRouterClient).enableCommandRouting(argThat(list -> {
            return list.size() == 2;
        }), any());
        verify(commandRouterClient).enableCommandRouting(argThat(list -> {
            return list.size() == 1;
        }), any());
        assertThat(enabledTenants).containsExactly("tenant1", "tenant2", "tenant3");
    }

    /**
     * Verifies that if the command consumer factory is used within a Kubernetes container, the internal command
     * consumer is initialized with an adapter instance id containing the container id.
     */
    @Test
    void testFactoryCreatesInternalConsumersWithAdapterInstanceId() {
        final String containerId = getTestContainerId();
        final String shortContainerId = containerId.substring(0, 12);
        // GIVEN a scenario where the application runs in a Kubernetes container
        when(kubernetesContainerInfoProvider.getContainerId(any())).thenReturn(Future.succeededFuture(containerId));
        // WHEN starting the command consumer factory
        factory.start();

        // THEN the internal command consumer has been initialized with an adapter instance id containing the container id
        verify(internalCommandConsumerSupplier).apply(argThat(adapterInstanceId -> {
            return adapterInstanceId.contains(shortContainerId);
        }), any());
    }

    /**
     * Verifies that if the command consumer factory is used within a Kubernetes container, there are multiple
     * attempts being made to determine the container id on startup.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testFactoryRetriesGetContainerId() {
        final String containerId = getTestContainerId();
        final String shortContainerId = containerId.substring(0, 12);
        // GIVEN a scenario where the application runs in a Kubernetes container, but the container id can only be
        // determined on the 3rd attempt
        when(kubernetesContainerInfoProvider.getContainerId(any())).thenReturn(
                Future.failedFuture("some error"),
                Future.failedFuture("some error"),
                Future.succeededFuture(containerId));
        // WHEN starting the command consumer factory
        factory.start();
        // THEN the internal command consumer has been initialized with an adapter instance id containing the container id
        verify(internalCommandConsumerSupplier).apply(argThat(adapterInstanceId -> {
            return adapterInstanceId.contains(shortContainerId);
        }), any());
    }

    private static String getTestContainerId() {
        return UUID.randomUUID().toString().concat(UUID.randomUUID().toString()).replaceAll("-", "");
    }
}
