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
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
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

    private static final Vertx vertx = Vertx.vertx();
    private static DeviceRegistryHttpClient registry;
    private static IntegrationTestSupport helper;
    private String tenantId;
    private String deviceId;

    /**
     * Creates the HTTP client for accessing the registry.
     */
    @BeforeAll
    public static void setUpClient() {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();
        registry = helper.registry;
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo Meta information about the currently running test case.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());
        tenantId = helper.getRandomTenantId();
        deviceId = helper.getRandomDeviceId(tenantId);
    }

    /**
     * Removes the device that has been added by the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void removeDevice(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
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

        registry.registerDevice(tenantId, deviceId, device).onComplete(ctx.completing());
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

        registry.registerDevice(tenantId, device)
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        assertThat(httpResponse.getHeader(HttpHeaders.ETAG.toString())).isNotNull();
                        final String createdDeviceId = assertLocationHeader(httpResponse.headers(), tenantId);
                        helper.addDeviceIdForRemoval(tenantId, createdDeviceId);
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
        registry.registerDevice(tenantId, deviceId, device)
        .compose(ok -> {
            // now try to add the device again
            return registry.registerDevice(tenantId, deviceId, device, HttpURLConnection.HTTP_CONFLICT);
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
                        tenantId, deviceId, device, null,
                        HttpURLConnection.HTTP_BAD_REQUEST)
                .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request to register a device that contains unsupported properties
     * fails with a 400 status.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForUnknownProperties(final VertxTestContext ctx) {

        final JsonObject requestBody = JsonObject.mapFrom(new Device());
        requestBody.put("unexpected", "property");

        registry.registerDevice(
                tenantId,
                deviceId,
                requestBody,
                "application/json",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request to create a request with non valid name fails fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidDeviceId(final VertxTestContext ctx) {

        registry.registerDevice(tenantId, "device ID With spaces and $pecial chars!", null, HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request to create a request with non valid name fails fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidTenantId(final VertxTestContext ctx) {

        registry.registerDevice("tenant ID With spaces and $pecial chars!", deviceId, null, HttpURLConnection.HTTP_BAD_REQUEST)
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

        registry.registerDevice(tenantId, deviceId, null, HttpURLConnection.HTTP_CREATED)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a device can be registered if the request
     * does not contain a body nor a content type.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceedsForEmptyBodyAndContentType(final VertxTestContext ctx) {

        registry.registerDevice(tenantId, deviceId, (Device) null, null, HttpURLConnection.HTTP_CREATED)
            .onComplete(ctx.completing());
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

        registry.registerDevice(tenantId, deviceId, device)
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId))
            .onComplete(ctx.succeeding(httpResponse -> {
                ctx.verify(() -> assertRegistrationInformation(httpResponse.bodyAsJson(Device.class), device));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request for registration information fails for
     * a device that is not registered.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.getRegistrationInfo(tenantId, "non-existing-device", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request for registration information fails for
     * a request that does not contain a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForMissingDeviceId(final VertxTestContext ctx) {

        registry.getRegistrationInfo(tenantId, null, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.completing());
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
        final AtomicReference<String> latestVersion = new AtomicReference<>();

        registry.registerDevice(tenantId, deviceId, originalData.mapTo(Device.class))
            .compose(httpResponse -> {
                latestVersion.set(httpResponse.getHeader(HttpHeaders.ETAG.toString()));
                assertThat(latestVersion.get()).isNotNull();
                return registry.updateDevice(tenantId, deviceId, updatedData);
            })
            .compose(httpResponse -> {
                final String updatedVersion = httpResponse.getHeader(HttpHeaders.ETAG.toString());
                assertThat(updatedVersion).isNotNull();
                assertThat(updatedVersion).isNotEqualTo(latestVersion.get());
                return registry.getRegistrationInfo(tenantId, deviceId);
            })
            .onComplete(ctx.succeeding(httpResponse -> {
                ctx.verify(() -> assertRegistrationInformation(httpResponse.bodyAsJson(Device.class),
                        updatedData.mapTo(Device.class)));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an update request fails if the device does not exist.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.updateDevice(
                tenantId,
                "non-existing-device",
                new JsonObject().put("ext", new JsonObject().put("test", "test")),
                CrudHttpClient.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that an update request fails if it doesn't contain a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingDeviceId(final VertxTestContext ctx) {

        registry.updateDevice(tenantId, null, new JsonObject(), CrudHttpClient.CONTENT_TYPE_JSON, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that an update request fails if it contains no content type.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingContentType(final VertxTestContext context) {

        registry.registerDevice(tenantId, deviceId, new Device())
            .compose(ok -> {
                // now try to update the device with missing content type
                final JsonObject requestBody = new JsonObject()
                        .put("ext", new JsonObject()
                                .put("test", "testUpdateDeviceFailsForMissingContentType")
                                .put("newKey1", "newValue1"));
                return registry.updateDevice(tenantId, deviceId, requestBody, null, HttpURLConnection.HTTP_BAD_REQUEST);
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

        registry.registerDevice(tenantId, deviceId, new Device())
            .compose(ok -> registry.deregisterDevice(tenantId, deviceId))
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request to deregister a device fails if it doesn't contain a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForMissingDeviceId(final VertxTestContext ctx) {

        registry.deregisterDevice(tenantId, null, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request to deregister a non-existing device fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.deregisterDevice(tenantId, "non-existing-device", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.completing());
    }

    private static String assertLocationHeader(final MultiMap responseHeaders, final String tenantId) {
        final String location = responseHeaders.get(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        final Pattern pattern = Pattern.compile("/(.*)/(.*)/(.*)/(.*)");
        final Matcher matcher = pattern.matcher(location);
        assertThat(matcher.matches());
        assertThat(matcher.group(2)).isEqualTo(RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);
        assertThat(matcher.group(3)).isEqualTo(tenantId);
        final String generatedId = matcher.group(4);
        assertThat(generatedId).isNotNull();
        return generatedId;
    }

    private static void assertRegistrationInformation(final Device response, final Device expectedData) {

        final Comparator<Instant> close = (Instant d1, Instant d2) -> d1.compareTo(d2) < 1000 ? 0 : 1;

        assertThat(response)
                .usingRecursiveComparison()
                .withComparatorForFields(close, "status.creationTime", "status.lastUpdate")
                .isEqualTo(expectedData);
    }
}
