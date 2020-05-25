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
package org.eclipse.hono.tests.registry;

import org.eclipse.hono.client.DeviceConnectionClient;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Connection service's AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class DeviceConnectionAmqpIT extends DeviceConnectionApiTests {

    private static final Vertx vertx = Vertx.vertx();

    private static DeviceConnectionClientFactory client;

    /**
     * Connects the factory.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void prepareDeviceRegistry(final VertxTestContext ctx) {

        client = DeviceConnectionClientFactory.create(
                HonoConnection.newConnection(
                        vertx,
                        IntegrationTestSupport.getDeviceRegistryProperties(
                                IntegrationTestSupport.HONO_USER,
                                IntegrationTestSupport.HONO_PWD)));

        client.connect().onComplete(ctx.completing());
    }

    /**
     * Logs the name of the current test.
     *
     * @param info The test meta data.
     */
    @BeforeEach
    public void info(final TestInfo info) {
        log.info("running test: {}", info.getDisplayName());
    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        final Checkpoint connections = ctx.checkpoint();
        disconnect(ctx, connections, client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<DeviceConnectionClient> getClient(final String tenant) {
        return client.getOrCreateDeviceConnectionClient(tenant);
    }

}
