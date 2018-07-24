/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests verifying the behavior of the Device Registry component's Device Registration AMQP endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class DeviceRegistrationAmqpIT {

    private static final String PROPERTY_VIA = "via";
    private static final Vertx vertx = Vertx.vertx();

    private static IntegrationTestSupport helper;
    private static HonoClient deviceRegistryclient;

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Starts the device registry and connects a client.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient(ctx);

        deviceRegistryclient = DeviceRegistryAmqpTestSupport.prepareDeviceRegistryClient(vertx,
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);
        deviceRegistryclient.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Removes all temporary objects from the registry.
     * 
     * @param ctx The vert.x test context.
     */
    @After
    public void cleanUp(final TestContext ctx) {
        helper.deleteObjects(ctx);
    }

    /**
     * Shuts down the device registry and closes the client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        DeviceRegistryAmqpTestSupport.shutdownDeviceRegistryClient(ctx, vertx, deviceRegistryclient);
    }

    /**
     * Verifies that the registry succeeds a request to assert a
     * device's registration status.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationSucceedsForDevice(final TestContext ctx) {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
            .compose(ok -> deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.assertRegistration(deviceId))
            .setHandler(ctx.asyncAssertSuccess(resp -> {
                ctx.assertEquals(deviceId, resp.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID));
                ctx.assertNotNull(resp.getString(RegistrationConstants.FIELD_ASSERTION));
            }));
    }

    /**
     * Verifies that the registry fails to assert a non-existing device's
     * registration status with a 404 error code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForUnknownDevice(final TestContext ctx) {
        deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT)
            .compose(client -> client.assertRegistration("non-existing-device"))
            .setHandler(ctx.asyncAssertFailure(t -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND, ctx)));
    }

    /**
     * Verifies that the registry fails to assert a disabled device's
     * registration status with a 404 error code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForDisabledDevice(final TestContext ctx) {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        helper.registry.registerDevice(
                Constants.DEFAULT_TENANT,
                deviceId,
                new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false))
            .compose(ok -> deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.assertRegistration(deviceId))
            .setHandler(ctx.asyncAssertFailure(t -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND, ctx)));
    }

    /**
     * Verifies that the registry fails a non-existing gateway's request to assert a
     * device's registration status with a 403 error code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForNonExistingGateway(final TestContext ctx) {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
            .compose(ok -> deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.assertRegistration(deviceId, "non-existing-gateway"))
            .setHandler(ctx.asyncAssertFailure(t -> assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN, ctx)));
    }

    /**
     * Verifies that the registry succeeds a request to assert the
     * registration status of a device that is connected via a gateway.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationSucceedsForGateway(final TestContext ctx) {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String gatewayId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        helper.registry.registerDevice(Constants.DEFAULT_TENANT, gatewayId)
            .compose(ok -> helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId, new JsonObject()
                    .put(PROPERTY_VIA, gatewayId)))
            .compose(ok -> deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.assertRegistration(deviceId, gatewayId))
            .setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the registry fails a disabled gateway's request to assert a
     * device's registration status with a 403 error code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForDisabledGateway(final TestContext ctx) {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String gatewayId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        helper.registry.registerDevice(Constants.DEFAULT_TENANT, gatewayId, new JsonObject()
                .put(RegistrationConstants.FIELD_ENABLED, false))
            .compose(ok -> helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId, new JsonObject()
                    .put(PROPERTY_VIA, gatewayId)))
            .compose(ok -> deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.assertRegistration(deviceId, gatewayId))
            .setHandler(ctx.asyncAssertFailure(t -> assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN, ctx)));
    }

    /**
     * Verifies that the registry fails a gateway's request to assert a
     * device's registration status for which it is not authorized with a 403 error code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAssertRegistrationFailsForUnauthorizedGateway(final TestContext ctx) {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String gatewayId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        helper.registry.registerDevice(Constants.DEFAULT_TENANT, gatewayId)
            .compose(ok -> helper.registry.registerDevice(Constants.DEFAULT_TENANT, deviceId, new JsonObject()
                    .put(PROPERTY_VIA, "not-" + gatewayId)))
            .compose(ok -> deviceRegistryclient.getOrCreateRegistrationClient(Constants.DEFAULT_TENANT))
            .compose(client -> client.assertRegistration(deviceId, gatewayId))
            .setHandler(ctx.asyncAssertFailure(t -> assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN, ctx)));
    }

    private static void assertErrorCode(final Throwable error, final int expectedErrorCode, final TestContext ctx) {
        ctx.assertTrue(error instanceof ServiceInvocationException);
        ctx.assertEquals(expectedErrorCode, ((ServiceInvocationException) error).getErrorCode());
    }
}
