/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClient;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonReceiver;

/**
 * Tests verifying behavior of {@link ProtonBasedAmqpAdapterClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class ProtonBasedAmqpAdapterClientTest extends AmqpAdapterClientTestBase {

    private ProtonBasedAmqpAdapterClient client;

    /**
     * Creates the client.
     */
    @BeforeEach
    public void createClient() {

        client = new ProtonBasedAmqpAdapterClient(connection);
    }

    /**
     * Verifies that the factories create method returned an instance.
     */
    @Test
    public void testCreateReturnsInstance() {
        assertThat(AmqpAdapterClient.create(connection)).isNotNull();
    }

    /**
     * Verifies that a device scoped command consumer instance is returned.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @CsvSource({",my-device", "my-tenant,my-device"})
    public void testCreateDeviceSpecificCommandConsumerReturnsInstance(
            final String tenantId,
            final String deviceId,
            final VertxTestContext ctx) {

        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(), any(), any())).thenReturn(Future.succeededFuture(receiver));

        client.createDeviceSpecificCommandConsumer(tenantId, deviceId, msg -> {})
            .onComplete(ctx.succeeding(consumer -> {
                ctx.verify(() -> assertThat(consumer).isNotNull());
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a command consumer instance is returned.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateCommandConsumerReturnsInstance(final VertxTestContext ctx) {

        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(), any(), any())).thenReturn(Future.succeededFuture(receiver));

        client.createCommandConsumer(x -> {})
            .onComplete(ctx.succeeding(consumer -> {
                ctx.verify(() -> assertThat(consumer).isNotNull());
                ctx.completeNow();
            }));
    }

}
