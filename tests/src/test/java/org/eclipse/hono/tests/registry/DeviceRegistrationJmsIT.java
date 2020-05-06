/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.tests.jms.JmsBasedRegistrationClient;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Device Registration AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class DeviceRegistrationJmsIT extends DeviceRegistrationApiTests {

    private static final Vertx vertx = Vertx.vertx();
    private static IntegrationTestSupport helper;
    private static JmsBasedHonoConnection registrationConnection;
    private static ClientConfigProperties props;

    /**
     * Starts the device registry and connects a client.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void init(final VertxTestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();

        props = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.HONO_USER,
                IntegrationTestSupport.HONO_PWD);

        registrationConnection = JmsBasedHonoConnection.newConnection(props);
        registrationConnection.connect().onComplete(ctx.completing());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IntegrationTestSupport getHelper() {
        return helper;
    }

    private Future<JmsBasedRegistrationClient> getJmsBasedClient(final String tenant) {
        if (registrationConnection == null) {
            throw new IllegalStateException("no connection to Device Registration service");
        }
        return registrationConnection
                .isConnected()
                .compose(ok -> JmsBasedRegistrationClient.create(registrationConnection, props, tenant));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<RegistrationClient> getClient(final String tenant) {
        return getJmsBasedClient(tenant).map(client -> (RegistrationClient) client);
    }

    /**
     * Removes all temporary objects from the registry.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void cleanUp(final VertxTestContext ctx) {
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
        final Checkpoint cons = ctx.checkpoint();
        disconnect(ctx, cons, registrationConnection);
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

        getJmsBasedClient(Constants.DEFAULT_TENANT)
        .compose(client -> client.sendRequest(RegistrationConstants.ACTION_ASSERT, null, null))
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

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);

        helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
        .compose(ok -> getJmsBasedClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.sendRequest(
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

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);

        helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
        .compose(ok -> getJmsBasedClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.sendRequest(
                "unsupported-operation",
                Collections.singletonMap(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId),
                null))
        .onComplete(ctx.failing(t -> {
            DeviceRegistrationApiTests.assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
            ctx.completeNow();
        }));
    }
}

