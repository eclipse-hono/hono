/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.impl.HonoClientUnitTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link AmqpAdapterClientFactory}.
 *
 */
public class AmqpAdapterClientFactoryTest {

    private static AmqpAdapterClientFactory factory;

    /**
     * Sets up the fixture.
     */
    @BeforeAll
    public static void setUp() {

        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(mock(Vertx.class));
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());

        final ProtonSender protonSender = HonoClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(any(), any(), any())).thenReturn(Future.succeededFuture(protonSender));

        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(), any(), any())).thenReturn(Future.succeededFuture(receiver));

        factory = AmqpAdapterClientFactory.create(connection, "my-tenant", SendMessageSampler.Factory.noop());
    }

    /**
     * Verifies that the factories create method returned an instance.
     */
    @Test
    public void testCreateReturnsInstance() {
        assertThat(factory).isNotNull();
    }

    /**
     * Verifies that a telemetry sender instance is returned.
     */
    @Test
    public void testGetOrCreateTelemetrySenderReturnsInstance() {
        final Future<TelemetrySender> sender = factory.getOrCreateTelemetrySender();
        assertThat(sender.succeeded()).isTrue();
        assertThat(sender.result()).isNotNull();
    }

    /**
     * Verifies that an event sender instance is returned.
     */
    @Test
    public void testGetOrCreateEventSenderReturnsInstance() {
        final Future<EventSender> sender = factory.getOrCreateEventSender();
        assertThat(sender.succeeded()).isTrue();
        assertThat(sender.result()).isNotNull();
    }

    /**
     * Verifies that a device scoped command consumer instance is returned.
     */
    @Test
    public void testCreateDeviceSpecificCommandConsumerReturnsInstance() {
        final Future<MessageConsumer> commandConsumer = factory.createDeviceSpecificCommandConsumer("my-device", x -> {
        });
        assertThat(commandConsumer.succeeded()).isTrue();
        assertThat(commandConsumer.result()).isNotNull();
    }

    /**
     * Verifies that a command consumer instance is returned.
     */
    @Test
    public void testCreateCommandConsumerReturnsInstance() {
        final Future<MessageConsumer> commandConsumer = factory.createCommandConsumer(x -> {
        });
        assertThat(commandConsumer.succeeded()).isTrue();
        assertThat(commandConsumer.result()).isNotNull();
    }

    /**
     * Verifies that a command response sender instance is returned.
     */
    @Test
    public void testGetOrCreateCommandResponseSender() {
        final Future<CommandResponder> responseSender = factory.getOrCreateCommandResponseSender();
        assertThat(responseSender.succeeded()).isTrue();
        assertThat(responseSender.result()).isNotNull();
    }
}
