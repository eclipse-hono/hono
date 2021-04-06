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


package org.eclipse.hono.adapter.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ReconnectListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;


/**
 * Tests verifying behavior of {@link CommandRouterCommandConsumerFactory}.
 *
 */
class CommandRouterCommandConsumerFactoryTest {

    private CommandRouterClient commandRouterClient;
    private CommandRouterCommandConsumerFactory factory;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        commandRouterClient = mock(CommandRouterClient.class, withSettings().extraInterfaces(ConnectionLifecycle.class));
        when(commandRouterClient.registerCommandConsumer(anyString(), anyString(), anyString(), any(Duration.class), any()))
            .thenReturn(Future.succeededFuture());
        factory = new CommandRouterCommandConsumerFactory(commandRouterClient, "test-adapter");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testFactoryReenablesCommandRouting() {

        // GIVEN a consumer factory with command consumers registered for 3 tenants
        final ConnectionLifecycle<HonoConnection> conLifecycle = (ConnectionLifecycle<HonoConnection>) commandRouterClient;
        final ArgumentCaptor<ReconnectListener<HonoConnection>> reconnectListener = ArgumentCaptor.forClass(ReconnectListener.class);
        verify(conLifecycle).addReconnectListener(reconnectListener.capture());
        factory.setMaxTenantIdsPerRequest(2);

        factory.createCommandConsumer("tenant1", "device1", "adapter", ctx -> {}, Duration.ofMinutes(10), null);
        factory.createCommandConsumer("tenant2", "device2", "adapter", ctx -> {}, Duration.ofMinutes(10), null);
        factory.createCommandConsumer("tenant3", "device3", "adapter", ctx -> {}, Duration.ofMinutes(10), null);

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
        assertThat(enabledTenants).containsExactlyInAnyOrder("tenant1", "tenant2", "tenant3");
    }
}
