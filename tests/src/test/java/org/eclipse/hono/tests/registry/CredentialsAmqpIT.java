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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Credentials AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class CredentialsAmqpIT extends CredentialsApiTests {

    private static CredentialsClientFactory clientFactory;

    /**
     * Creates an AMQP 1.0 based client for the Credentials API.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createCredentialsClientFactory(final VertxTestContext ctx) {

        clientFactory = CredentialsClientFactory.create(
                HonoConnection.newConnection(
                        VERTX,
                        IntegrationTestSupport.getDeviceRegistryProperties(
                                IntegrationTestSupport.TENANT_ADMIN_USER,
                                IntegrationTestSupport.TENANT_ADMIN_PWD)));

        clientFactory.connect().onComplete(ctx.completing());

    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        final Checkpoint connections = ctx.checkpoint();
        disconnect(ctx, connections, clientFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<CredentialsClient> getClient(final String tenant) {
        return clientFactory.getOrCreateCredentialsClient(tenant);
    }
}
