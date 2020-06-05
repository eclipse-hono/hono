/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * device registration HTTP endpoint and validating the corresponding responses.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class DeviceManagementIT {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceManagementIT.class);

    private static final String TENANT = Constants.DEFAULT_TENANT;

    private static Vertx vertx = Vertx.vertx();
    private static DeviceRegistryHttpClient registry;

    private String deviceId;

    /**
     * Creates the HTTP client for accessing the registry.
     */
    @BeforeAll
    public static void setUpClient() {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo Meta information about the currently running test case.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
        deviceId = UUID.randomUUID().toString();
    }

    /**
     * Removes the device that has been added by the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void removeDevice(final VertxTestContext ctx) {
        registry.deregisterDevice(TENANT, deviceId)
        .onComplete(s -> ctx.completeNow());
    }

    /**
     * Shuts down the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void tearDown(final VertxTestContext ctx) {
        vertx.close(ctx.completing());
    }

    /**
     * Verifies that a device can be properly registered.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceeds(final VertxTestContext ctx) {

        final Device device = new Device();
        device.putExtension("test", "test");

        registry.registerDevice(TENANT, deviceId, device).onComplete(ctx.completing());
    }

    /**
     * Verifies that a device can be properly registered without providing a payload.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceWithoutPayloadSucceeds(final VertxTestContext ctx) {

        registry.registerDevice(TENANT, deviceId, (Device) null, null, HttpURLConnection.HTTP_CREATED)
                .onComplete(ctx.completing());
    }

    /**
     * Verifies that a device can be registered if the request body does not contain a device identifier.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceedsWithoutDeviceId(final VertxTestContext ctx) {

        final Device device = new Device();
        device.putExtension("test", "test");

        registry.registerDevice(TENANT, null, device)
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        final List<String> locations = s.getAll("location");
                        assertThat(locations).isNotNull();
                        assertThat(locations).hasSize(1);
                        final String location = locations.get(0);
                        assertThat(location).isNotNull();
                        assertThat(location.matches("/(.*)/(.*)/(.*)/(.*)"));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a device can be registered only once.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForDuplicateDevice(final VertxTestContext ctx) {

        final Device device = new Device();
        // add the device
        registry.registerDevice(TENANT, deviceId, device)
        .compose(ok -> {
            // now try to add the device again
            return registry.registerDevice(TENANT, deviceId, device, HttpURLConnection.HTTP_CONFLICT);
        })
        .onComplete(ctx.completing());
    }

    /**
     * Verifies that a device cannot be registered if the request does not contain a content type.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForMissingContentType(final VertxTestContext ctx) {

        final Device device = new Device();
        device.putExtension("test", "testAddDeviceFailsForMissingContentType");

        registry
                .registerDevice(
                        TENANT, deviceId, device, null,
                        HttpURLConnection.HTTP_BAD_REQUEST)
                .onComplete(ctx.completing());
    }

    /**
     * Verifies that a device can be registered if the request
     * does not contain a body.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceedsForEmptyBody(final VertxTestContext ctx) {

        registry.registerDevice(TENANT, deviceId, null, HttpURLConnection.HTTP_CREATED).onComplete(ctx.completing());
    }

    /**
     * Verifies that a device can be registered if the request
     * does not contain a body nor a content type.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceedsForEmptyBodyAndContentType(final VertxTestContext ctx) {

        registry.registerDevice(TENANT, deviceId, (Device) null, null, HttpURLConnection.HTTP_CREATED).onComplete(ctx.completing());
    }

    /**
     * Verifies that the information that has been registered for a device
     * is contained in the result when retrieving registration information
     * for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceContainsRegisteredInfo(final VertxTestContext ctx) {

        final Device device = new Device();
        device.putExtension("testString", "testValue");
        device.putExtension("testBoolean", false);
        device.setEnabled(false);

        registry.registerDevice(TENANT, deviceId, device)
            .compose(ok -> registry.getRegistrationInfo(TENANT, deviceId))
            .compose(info -> {
                    assertRegistrationInformation(ctx, info.toJsonObject().mapTo(Device.class), deviceId, device);
                return Future.succeededFuture();
            }).onComplete(ctx.completing());
    }

    /**
     * Verifies that a request for registration information fails for
     * a device that is not registered.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.getRegistrationInfo(TENANT, "non-existing-device")
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request for registration information fails for
     * a request that does not contain a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForMissingDeviceId(final VertxTestContext ctx) {

        registry.getRegistrationInfo(TENANT, null)
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the registration information provided when updating
     * a device replaces the existing information.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceSucceeds(final VertxTestContext ctx) {

        final JsonObject originalData = new JsonObject()
                .put("ext", new JsonObject()
                        .put("key1", "value1")
                        .put("key2", "value2"))
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject updatedData = new JsonObject()
                .put("ext", new JsonObject()
                        .put("newKey1", "newValue1"))
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);

        registry.registerDevice(TENANT, deviceId, originalData.mapTo(Device.class))
            .compose(ok -> registry.updateDevice(TENANT, deviceId, updatedData))
            .compose(ok -> registry.getRegistrationInfo(TENANT, deviceId))
            .compose(info -> {
                    assertRegistrationInformation(ctx, info.toJsonObject().mapTo(Device.class), deviceId,
                            updatedData.mapTo(Device.class));
                return Future.succeededFuture();
            }).onComplete(ctx.completing());
    }

    /**
     * Verifies that an update request fails if the device does not exist.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.updateDevice(TENANT, "non-existing-device", new JsonObject().put("ext", new JsonObject().put("test", "test")))
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that an update request fails if it doesn't contain a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingDeviceId(final VertxTestContext ctx) {

        registry.updateDevice(TENANT, null, new JsonObject())
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that an update request fails if it contains no content type.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingContentType(final VertxTestContext context) {

        registry.registerDevice(TENANT, deviceId, new Device())
            .compose(ok -> {
                // now try to update the device with missing content type
                    final JsonObject requestBody = new JsonObject()
                            .put("ext", new JsonObject()
                                    .put("test", "testUpdateDeviceFailsForMissingContentType")
                                    .put("newKey1", "newValue1"));
                return registry.updateDevice(TENANT, deviceId, requestBody, null, HttpURLConnection.HTTP_BAD_REQUEST);
            }).onComplete(context.completing());
    }

    /**
     * Verifies that no registration info can be retrieved anymore
     * once a device has been deregistered.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceSucceeds(final VertxTestContext ctx) {

        registry.registerDevice(TENANT, deviceId, new Device())
        .compose(ok -> registry.deregisterDevice(TENANT, deviceId))
        .compose(ok -> registry.getRegistrationInfo(TENANT, deviceId))
        .onComplete(getAttempt -> {
            if (getAttempt.succeeded()) {
                ctx.failNow(new AssertionError("should not have found registration"));
            } else {
                ctx.verify(() -> {
                    assertThat(((ServiceInvocationException) getAttempt.cause()).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                });
                ctx.completeNow();
            }
        });
    }

    /**
     * Verifies that a request to deregister a device fails if it doesn't contain a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForMissingDeviceId(final VertxTestContext ctx) {

        registry.deregisterDevice(TENANT, null)
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to deregister a non-existing device fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.deregisterDevice(TENANT, "non-existing-device").onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to create a request with non valid name fails fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidDeviceId(final VertxTestContext ctx) {

        registry.registerDevice(TENANT, "device ID With spaces and $pecial chars!", null)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to create a request with non valid name fails fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidTenantId(final VertxTestContext ctx) {

        registry.registerDevice("tenant ID With spaces and $pecial chars!", deviceId, null)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST));
                ctx.completeNow();
            }));
    }

    private static void assertRegistrationInformation(
            final VertxTestContext ctx,
            final Device response,
            final String expectedDeviceId,
            final Device expectedData) {

        assertThat(response).usingRecursiveComparison().isEqualTo(expectedData);
    }
}
