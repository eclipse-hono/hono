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

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Tenant AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class TenantAmqpIT extends TenantApiTests {

    private static TenantClientFactory allTenantClientFactory;
    private static TenantClientFactory defaultTenantClientFactory;
    private static TenantClient allTenantClient;
    private static TenantClient defaultTenantClient;

    /**
     * Creates {@link TenantClient}s for invoking operations of the
     * Tenant API.
     *
     * @param vertx The vert.x instance to run the clients on.
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createTenantClientFactories(final Vertx vertx, final VertxTestContext ctx) {

        final Checkpoint connections = ctx.checkpoint(2);

        allTenantClientFactory = TenantClientFactory.create(
                HonoConnection.newConnection(
                        vertx,
                        IntegrationTestSupport.getDeviceRegistryProperties(
                                IntegrationTestSupport.TENANT_ADMIN_USER,
                                IntegrationTestSupport.TENANT_ADMIN_PWD)));

        allTenantClientFactory
        .connect()
        .compose(c -> allTenantClientFactory.getOrCreateTenantClient())
        .onComplete(ctx.succeeding(r -> {
            allTenantClient = r;
            connections.flag();
        }));

        defaultTenantClientFactory = TenantClientFactory.create(
                HonoConnection.newConnection(
                        vertx,
                        IntegrationTestSupport.getDeviceRegistryProperties(
                                IntegrationTestSupport.HONO_USER,
                                IntegrationTestSupport.HONO_PWD)));

        defaultTenantClientFactory
        .connect()
        .compose(c -> defaultTenantClientFactory.getOrCreateTenantClient())
        .onComplete(ctx.succeeding(r -> {
            defaultTenantClient = r;
            connections.flag();
        }));
    }

    /**
     * Shuts down the device registry and closes the tenantClientFactory.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {

        final Checkpoint connections = ctx.checkpoint(2);
        disconnect(ctx, connections, allTenantClientFactory);
        disconnect(ctx, connections, defaultTenantClientFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TenantClient getAdminClient() {
        return allTenantClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TenantClient getRestrictedClient() {
        return defaultTenantClient;
    }
}
