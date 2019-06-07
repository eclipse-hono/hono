/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.tests.registry.legacy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.HttpURLConnection;
import java.util.UUID;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.LegacyDeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * device registration HTTP endpoint and validating the corresponding responses.
 */
@Deprecated
@RunWith(VertxUnitRunner.class)
public class LegacyDeviceRegistrationHttpIT {

    private static final String TENANT = Constants.DEFAULT_TENANT;

    private static Vertx vertx = Vertx.vertx();
    private static LegacyDeviceRegistryHttpClient registry;

    /**
     * Set the timeout for all test methods by using a JUnit Rule (instead of providing the timeout at every @Test annotation).
     * See {@link Test#timeout} for details about improved thread safety regarding the @After annotation for each test.
     */
    @Rule
    public final Timeout timeoutForAllMethods = Timeout.seconds(5);

    private String deviceId;

    /**
     * Creates the HTTP client for accessing the registry.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUpClient(final TestContext ctx) {

        registry = new LegacyDeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        deviceId = UUID.randomUUID().toString();
    }

    /**
     * Removes the device that has been added by the test.
     *
     * @param ctx The vert.x test context.
     */
    @After
    public void removeDevice(final TestContext ctx) {
        final Async deletion = ctx.async();
        registry.deregisterDevice(TENANT, deviceId).setHandler(attempt -> deletion.complete());
        deletion.await();
    }

    /**
     * Shuts down the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void tearDown(final TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a device can be properly registered.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceeds(final TestContext ctx) {

        final JsonObject requestBody = new JsonObject()
                .put("test", "test");

        registry.registerDevice(TENANT, deviceId, requestBody).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a device cannot be registered if the request body
     * does not contain a device identifier.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsWithoutDeviceId(final TestContext ctx) {

        registry.registerDevice(TENANT, null, new JsonObject().put("test", "test"), HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a device can be registered only once.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForDuplicateDevice(final TestContext ctx) {

        final JsonObject data = new JsonObject();
        // add the device
        registry.registerDevice(TENANT, deviceId, data).setHandler(ctx.asyncAssertSuccess(s -> {
            // now try to add the device again
            registry.registerDevice(TENANT, deviceId, data, HttpURLConnection.HTTP_CONFLICT).setHandler(ctx.asyncAssertSuccess());
        }));
    }

    /**
     * Verifies that a device cannot be registered if the request
     * does not contain a content type.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForMissingContentType(final TestContext ctx) {

        registry.registerDevice(
                TENANT,
                deviceId,
                new JsonObject().put("key", "value"),
                null,
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a device cannot be registered if the request
     * does not contain a body.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForMissingBody(final TestContext ctx) {

        registry.registerDevice(TENANT, deviceId, null, HttpURLConnection.HTTP_BAD_REQUEST).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the information that has been registered for a device
     * is contained in the result when retrieving registration information
     * for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceContainsRegisteredInfo(final TestContext ctx) {

        final JsonObject data = new JsonObject()
                .put("testString", "testValue")
                .put("testBoolean", Boolean.FALSE)
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);

        registry.registerDevice(TENANT, deviceId, data)
                .compose(ok -> registry.getRegistrationInfo(TENANT, deviceId))
                .compose(info -> {
                    assertRegistrationInformation(ctx, info.toJsonObject(), deviceId, data);
                    return Future.succeededFuture();
                }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a request for registration information fails for
     * a device that is not registered.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForNonExistingDevice(final TestContext ctx) {

        registry.getRegistrationInfo(TENANT, "non-existing-device").setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the registration information provided when updating
     * a device replaces the existing information.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceSucceeds(final TestContext ctx) {

        final JsonObject originalData = new JsonObject()
                .put("key1", "value1")
                .put("key2", "value2")
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject updatedData = new JsonObject()
                .put("newKey1", "newValue1")
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);

        registry.registerDevice(TENANT, deviceId, originalData)
                .compose(ok -> registry.updateDevice(TENANT, deviceId, updatedData))
                .compose(ok -> registry.getRegistrationInfo(TENANT, deviceId))
                .compose(info -> {
                    assertRegistrationInformation(ctx, info.toJsonObject(), deviceId, updatedData);
                    return Future.succeededFuture();
                }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that an update request fails if the device does not exist.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForNonExistingDevice(final TestContext ctx) {

        registry.updateDevice(TENANT, "non-existing-device", new JsonObject().put("test", "test")).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that an update request fails if it contains no content type.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingContentType(final TestContext context) {

        registry.registerDevice(TENANT, deviceId, new JsonObject())
                .compose(ok -> {
                    // now try to update the device with missing content type
                    final JsonObject requestBody = new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId).put("newKey1", "newValue1");
                    return registry.updateDevice(TENANT, deviceId, requestBody, null, HttpURLConnection.HTTP_BAD_REQUEST);
                }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that no registration info can be retrieved anymore
     * once a device has been deregistered.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceSucceeds(final TestContext ctx) {

        registry.registerDevice(TENANT, deviceId, new JsonObject())
                .compose(ok -> registry.deregisterDevice(TENANT, deviceId))
                .compose(ok -> {
                    return registry.getRegistrationInfo(TENANT, deviceId)
                            .compose(info -> Future.failedFuture("get registration info should have failed"))
                            .recover(t -> {
                                ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
                                return Future.succeededFuture();
                            });
                }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a request to deregister a non-existing device fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForNonExisingDevice(final TestContext ctx) {

        registry.deregisterDevice(TENANT, "non-existing-device").setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private static void assertRegistrationInformation(
            final TestContext ctx,
            final JsonObject response,
            final String expectedDeviceId,
            final JsonObject expectedData) {

        ctx.assertEquals(expectedDeviceId, response.getString(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID));
        final JsonObject registeredData = response.getJsonObject(RegistrationConstants.FIELD_DATA);
        registeredData.forEach(entry -> {
            ctx.assertEquals(expectedData.getValue(entry.getKey()), entry.getValue());
        });
    }
}
