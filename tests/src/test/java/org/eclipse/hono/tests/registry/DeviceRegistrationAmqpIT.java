/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Device Registration AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class DeviceRegistrationAmqpIT extends DeviceRegistrationApiTests {

    private static DeviceRegistrationClient registrationClient;

    /**
     * Creates a new client for accessing the Device Registration API.
     *
     * @param vertx The vert.x instance.
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createRegistrationClient(final Vertx vertx, final VertxTestContext ctx) {

        registrationClient = new ProtonBasedDeviceRegistrationClient(
                HonoConnection.newConnection(
                        vertx,
                        IntegrationTestSupport.getDeviceRegistryProperties(
                                IntegrationTestSupport.TENANT_ADMIN_USER,
                                IntegrationTestSupport.TENANT_ADMIN_PWD)),
                SendMessageSampler.Factory.noop(),
                null);
        registrationClient.start().onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        final Checkpoint cons = ctx.checkpoint();
        stop(ctx, cons, registrationClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DeviceRegistrationClient getClient() {
        return registrationClient;
    }
}

