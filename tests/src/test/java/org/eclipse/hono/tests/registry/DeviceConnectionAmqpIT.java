/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.registry;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.command.DeviceConnectionClient;
import org.eclipse.hono.client.command.amqp.ProtonBasedDeviceConnectionClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Connection service's AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class DeviceConnectionAmqpIT extends DeviceConnectionApiTests {

    private static DeviceConnectionClient client;

    /**
     * Creates the client.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createDeviceConnectionClient(final Vertx vertx, final VertxTestContext ctx) {

        client = new ProtonBasedDeviceConnectionClient(
                HonoConnection.newConnection(
                        vertx,
                        IntegrationTestSupport.getDeviceConnectionServiceProperties(
                                IntegrationTestSupport.TENANT_ADMIN_USER,
                                IntegrationTestSupport.TENANT_ADMIN_PWD)),
                SendMessageSampler.Factory.noop());

        client.start().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        final Checkpoint connections = ctx.checkpoint();
        stop(ctx, connections, client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DeviceConnectionClient getClient() {
        return client;
    }

}
