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

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.command.DeviceConnectionClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedDeviceConnectionClient;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.util.DeviceConnectionConstants.DeviceConnectionAction;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of a Device Connection service implementation using a JMS based client.
 */
@ExtendWith(VertxExtension.class)
public class DeviceConnectionJmsIT extends DeviceConnectionApiTests {

    private static JmsBasedDeviceConnectionClient client;

    /**
     * Creates a client for the Device Connection service.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createClient(final VertxTestContext ctx) {

        final var props = IntegrationTestSupport.getDeviceConnectionServiceProperties(
                IntegrationTestSupport.TENANT_ADMIN_USER,
                IntegrationTestSupport.TENANT_ADMIN_PWD);

        client = new JmsBasedDeviceConnectionClient(JmsBasedHonoConnection.newConnection(props), props);
        client.start().onComplete(ctx.completing());
    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        final Checkpoint cons = ctx.checkpoint();
        stop(ctx, cons, client);
    }

    private JmsBasedDeviceConnectionClient getJmsBasedClient() {
        if (client == null) {
            throw new IllegalStateException("no connection to Device Connection service");
        }
        return client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DeviceConnectionClient getClient() {
        return getJmsBasedClient();
    }

    /**
     * Verifies that a request to get the command-handling adapter instances
     * fails with a 400 status if the request contains malformed gateway IDs.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCommandHandlingAdapterInstancesFailsForMalformedPayload(final VertxTestContext ctx) {

        getJmsBasedClient().sendRequest(
                randomId(),
                DeviceConnectionAction.GET_CMD_HANDLING_ADAPTER_INSTANCES.getSubject(),
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, randomId()),
                Buffer.buffer(new byte[] { 0x01, 0x02, 0x03, 0x04 })) // no JSON
            .onComplete(ctx.failing(t -> {
                assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service responds with a 400 status to a request that
     * has no subject.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testRequestFailsForMissingSubject(final VertxTestContext ctx) {

        getJmsBasedClient().sendRequest(
                randomId(),
                null,
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, randomId()),
                null)
        .onComplete(ctx.failing(t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service responds with a 400 status to a request that
     * indicates an unsupported operation in its subject.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testRequestFailsForUnsupportedOperation(final VertxTestContext ctx) {

        getJmsBasedClient().sendRequest(
                randomId(),
                "unsupported-operation",
                Map.of(MessageHelper.APP_PROPERTY_DEVICE_ID, randomId()),
                null)
        .onComplete(ctx.failing(t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }
}

