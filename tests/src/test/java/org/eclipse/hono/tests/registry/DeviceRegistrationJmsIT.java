/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.tests.jms.JmsBasedRegistrationClient;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Device Registration AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class DeviceRegistrationJmsIT extends DeviceRegistrationApiTests {

    private static JmsBasedRegistrationClient registrationClient;

    /**
     * Creates a client for the Device Registration service.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createClient(final VertxTestContext ctx) {

        final var props = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.TENANT_ADMIN_USER,
                IntegrationTestSupport.TENANT_ADMIN_PWD);

        registrationClient = new JmsBasedRegistrationClient(JmsBasedHonoConnection.newConnection(props));
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

    private JmsBasedRegistrationClient getJmsBasedClient() {
        if (registrationClient == null) {
            throw new IllegalStateException("no connection to Device Registration service");
        }
        return registrationClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DeviceRegistrationClient getClient() {
        return getJmsBasedClient();
    }

    /**
     * Verifies that a request to assert a device's registration status which lacks
     * a device ID fails with a 400 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForMissingDeviceId(final VertxTestContext ctx) {

        getJmsBasedClient().sendRequest(tenantId, RegistrationConstants.ACTION_ASSERT, null, null)
            .onComplete(ctx.failing(t -> {
                DeviceRegistrationApiTests.assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request message which lacks a subject fails with a 400 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testRequestFailsForMissingSubject(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        getHelper().registry.registerDevice(tenantId, deviceId)
            .compose(ok -> getJmsBasedClient().sendRequest(
                    tenantId,
                    null,
                    Collections.singletonMap(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                    null))
            .onComplete(ctx.failing(t -> {
                DeviceRegistrationApiTests.assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request message which lacks a subject fails with a 400 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testRequestFailsForUnsupportedOperation(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        getHelper().registry.registerDevice(tenantId, deviceId)
            .compose(ok -> getJmsBasedClient().sendRequest(
                    tenantId,
                    "unsupported-operation",
                    Collections.singletonMap(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                    null))
            .onComplete(ctx.failing(t -> {
                DeviceRegistrationApiTests.assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
                ctx.completeNow();
            }));
    }
}

