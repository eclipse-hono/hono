/**
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
 */


package org.eclipse.hono.tests.registry;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Common test cases for the Device Registration API.
 *
 */
abstract class DeviceRegistrationApiTests extends DeviceRegistryTestBase {

    private static final String NON_EXISTING_DEVICE_ID = "non-existing-device";
    private static final String NON_EXISTING_GATWAY_ID = "non-existing-gateway";

    /**
     * Gets a client for interacting with the Device Registration service.
     * 
     * @param tenant The tenant to scope the client to.
     * @return The client.
     */
    protected abstract Future<RegistrationClient> getClient(String tenant);

    private Boolean isGatewayModeSupported() {

        return getHelper().isGatewayModeSupported();
    }

    /**
     * Verifies that the registry succeeds a request to assert a
     * device's registration status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationSucceedsForDevice(final VertxTestContext ctx) {

        final JsonObject defaults = new JsonObject()
                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/vnd.acme+json");
        final JsonObject deviceData = new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, defaults);
        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);

        getHelper().registry
        .registerDevice(Constants.DEFAULT_TENANT, deviceId, deviceData)
        .compose(r -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.assertRegistration(deviceId))
        .setHandler(ctx.succeeding(resp -> {
            ctx.verify(() -> {
                assertThat(resp.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID), is(deviceId));
                assertThat(resp.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS), is(defaults));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registry succeeds a request to assert the registration status
     * of a device that connects via an authorized gateway.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationSucceedsForDeviceViaGateway(final VertxTestContext ctx) {

        final String gatewayId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final JsonArray via = new JsonArray().add(gatewayId).add("another-gateway");

        getHelper().registry
        .registerDevice(Constants.DEFAULT_TENANT, gatewayId)
        .compose(ok -> getHelper().registry.registerDevice(
                Constants.DEFAULT_TENANT,
                deviceId,
                new JsonObject().put(RegistrationConstants.FIELD_VIA, via)))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.assertRegistration(deviceId, gatewayId))
        .setHandler(ctx.succeeding(resp -> {
            ctx.verify(() -> {
                assertThat(resp.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID), is(deviceId));
                assertThat(resp.getJsonArray(RegistrationConstants.FIELD_VIA), is(via));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registry fails to assert a non-existing device's
     * registration status with a 404 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForUnknownDevice(final VertxTestContext ctx) {

        getClient(Constants.DEFAULT_TENANT)
        .compose(client -> client.assertRegistration(NON_EXISTING_DEVICE_ID))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registry fails a non-existing gateway's request to assert a
     * device's registration status with a 403 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForNonExistingGateway(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);

        getHelper().registry
        .registerDevice(Constants.DEFAULT_TENANT, deviceId)
        .compose(r -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.assertRegistration(deviceId, NON_EXISTING_GATWAY_ID))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registry fails a disabled gateway's request to assert a
     * device's registration status with a 403 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForDisabledGateway(final VertxTestContext ctx) {

        assumeTrue(isGatewayModeSupported());

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String gatewayId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);

        getHelper().registry
        .registerDevice(Constants.DEFAULT_TENANT, gatewayId, new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false))
        .compose(ok -> getHelper().registry.registerDevice(
                Constants.DEFAULT_TENANT,
                deviceId,
                new JsonObject().put(RegistrationConstants.FIELD_VIA, gatewayId)))
        .compose(r -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.assertRegistration(deviceId, gatewayId))
        .setHandler(ctx.failing(t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registry fails a gateway's request to assert a
     * device's registration status for which it is not authorized with a 403 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForUnauthorizedGateway(final VertxTestContext ctx) {

        assumeTrue(isGatewayModeSupported());

        // Prepare the identities to insert
        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authorizedGateway = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String unauthorizedGateway = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);

        getHelper().registry
        .registerDevice(Constants.DEFAULT_TENANT, authorizedGateway)
        .compose(ok -> getHelper().registry.registerDevice(Constants.DEFAULT_TENANT, unauthorizedGateway))
        .compose(ok -> getHelper().registry.registerDevice(Constants.DEFAULT_TENANT, deviceId,
                new JsonObject().put(RegistrationConstants.FIELD_VIA, new JsonArray().add(authorizedGateway))))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.assertRegistration(deviceId, unauthorizedGateway))
        .setHandler(ctx.failing( t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registry fails to assert a disabled device's
     * registration status with a 404 error code.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testAssertRegistrationFailsForDisabledDevice(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);

        getHelper().registry
        .registerDevice(Constants.DEFAULT_TENANT, deviceId,
            new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.assertRegistration(deviceId))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Asserts that a given error is a {@link ServiceInvocationException}
     * with a particular error code.
     * 
     * @param error The error.
     * @param expectedErrorCode The error code.
     * @throws AssertionError if the assertion fails.
     */
    public static void assertErrorCode(final Throwable error, final int expectedErrorCode) {
        assertTrue(error instanceof ServiceInvocationException);
        assertThat(((ServiceInvocationException) error).getErrorCode(), is(expectedErrorCode));
    }
}
