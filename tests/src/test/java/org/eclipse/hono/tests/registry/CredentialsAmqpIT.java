/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
 * Tests verifying the behavior of the Device Registry component's Credentials AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class CredentialsAmqpIT extends CredentialsApiTests {

    private static final Vertx vertx = Vertx.vertx();

    private static CredentialsClientFactory client;
    private static IntegrationTestSupport helper;

    /**
     * Starts the device registry and connects a client.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void prepareDeviceRegistry(final VertxTestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();

        client = CredentialsClientFactory.create(
                HonoConnection.newConnection(
                        vertx,
                        IntegrationTestSupport.getDeviceRegistryProperties(
                                IntegrationTestSupport.HONO_USER,
                                IntegrationTestSupport.HONO_PWD)));

        client.connect().onComplete(ctx.completing());
    }

    /**
     * Logs the current test case's display name.
     *
     * @param testInfo The test case meta data.
     */
    @BeforeEach
    public void logTestName(final TestInfo testInfo) {
        log.info("running test {}", testInfo.getDisplayName());
    }

    /**
     * Remove the fixture from the device registry if the test had set up any.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void cleanupDeviceRegistry(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
        ctx.completeNow();
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
    protected IntegrationTestSupport getHelper() {
        return helper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<CredentialsClient> getClient(final String tenant) {
        return client.getOrCreateCredentialsClient(tenant);
    }
}
