/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceStatus;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.EnabledIfRegistrySupportsFeatures;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.type.TypeReference;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * device registration HTTP endpoint and validating the corresponding responses.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class DeviceManagementIT extends DeviceRegistryTestBase {

    private DeviceRegistryHttpClient registry;
    private String tenantId;
    private String deviceId;

    /**
     * Sets up the fixture.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeEach
    public void setUp(final VertxTestContext ctx) {

        registry = getHelper().registry;
        tenantId = getHelper().getRandomTenantId();
        deviceId = getHelper().getRandomDeviceId(tenantId);

        registry.addTenant(tenantId).onComplete(ctx.succeedingThenComplete());

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

        registry.registerDevice(tenantId, deviceId, device, HttpURLConnection.HTTP_CREATED)
            .onFailure(ctx::failNow)
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_OK))
            .onComplete(ctx.succeedingThenComplete());
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
                        getHelper().addDeviceIdForRemoval(tenantId, createdDeviceId);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to add a device fails if the given tenant does not exist.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForNonExistingTenant(final VertxTestContext ctx) {

        final Device device = new Device();

        registry.registerDevice(
                "non-existing-tenant",
                null,
                device,
                CrudHttpClient.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_NOT_FOUND)
            .compose(ok -> registry.registerDevice(
                    "non-existing-tenant",
                    deviceId,
                    device,
                    CrudHttpClient.CONTENT_TYPE_JSON,
                    HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
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
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
            ctx.completeNow();
        }));
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
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                    ctx.completeNow();
                }));
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
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to register a device with an invalid device identifier fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidDeviceId(final VertxTestContext ctx) {

        registry.registerDevice(tenantId, "device ID With spaces and $pecial chars!", null, HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to register a device with an invalid tenant identifier fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidTenantId(final VertxTestContext ctx) {

        registry.registerDevice("tenant ID With spaces and $pecial chars!", deviceId, null, HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to register a device with a body that exceeds the registry's max payload limit
     * fails with a {@link HttpURLConnection#HTTP_ENTITY_TOO_LARGE} status code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForRequestPayloadExceedingLimit(final VertxTestContext ctx)  {

        final Device device = new Device();
        final var data = new char[3000];
        Arrays.fill(data, 'x');
        device.setExtensions(Map.of("data", new String(data)));

        registry.registerDevice(tenantId, deviceId, device, HttpURLConnection.HTTP_ENTITY_TOO_LARGE)
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
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
            .onFailure(ctx::failNow)
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_OK))
            .onComplete(ctx.succeedingThenComplete());
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
            .onFailure(ctx::failNow)
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_OK))
            .onComplete(ctx.succeedingThenComplete());
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
                ctx.verify(() -> assertRegistrationInformation(httpResponse, device, null, false));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request for registration information fails if the given tenant does not exist.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForNonExistingTenant(final VertxTestContext ctx) {

        registry.getRegistrationInfo("non-existing-tenant", deviceId, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
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
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
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
        final AtomicReference<String> latestVersion = new AtomicReference<>();
        final AtomicReference<Instant> creationTime = new AtomicReference<>();

        registry.registerDevice(tenantId, deviceId, originalData.mapTo(Device.class))
                .compose(httpResponse -> {
                    latestVersion.set(httpResponse.getHeader(HttpHeaders.ETAG.toString()));
                    ctx.verify(() -> assertThat(latestVersion.get()).isNotNull());
                    return registry.getRegistrationInfo(tenantId, deviceId);
                })
                .compose(httpResponse -> {
                    final String resourceVersion = httpResponse.getHeader(HttpHeaders.ETAG.toString());
                    ctx.verify(() -> {
                        assertThat(latestVersion.get()).isEqualTo(resourceVersion);
                        final var deviceStatus = getDeviceStatus(httpResponse.bodyAsJsonObject());
                        assertThat(deviceStatus).isNotNull();
                        assertThat(deviceStatus.getCreationTime()).isNotNull();
                        assertThat(deviceStatus.getLastUpdate()).isNull();
                        creationTime.set(deviceStatus.getCreationTime());
                    });
                    return registry.updateDevice(tenantId, deviceId, updatedData);
                })
                .compose(httpResponse -> {
                    final String updatedVersion = httpResponse.getHeader(HttpHeaders.ETAG.toString());
                    ctx.verify(() -> {
                        assertThat(updatedVersion).isNotNull();
                        assertThat(updatedVersion).isNotEqualTo(latestVersion.get());
                        latestVersion.set(updatedVersion);
                    });
                    return registry.getRegistrationInfo(tenantId, deviceId);
                })
                .onComplete(ctx.succeeding(httpResponse -> {
                    final String resourceVersion = httpResponse.getHeader(HttpHeaders.ETAG.toString());
                    ctx.verify(() -> {
                        assertThat(latestVersion.get()).isEqualTo(resourceVersion);
                        assertRegistrationInformation(
                                httpResponse,
                                updatedData.mapTo(Device.class),
                                creationTime.get(),
                                true);
                        });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that an update request fails if the device does not exist.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForNonExistingTenant(final VertxTestContext ctx) {

        registry.updateDevice(
                "non-existing-tenant",
                deviceId,
                new JsonObject().put("ext", new JsonObject().put("test", "test")),
                CrudHttpClient.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
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
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an update request fails if it contains no content type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingContentType(final VertxTestContext ctx) {

        registry.registerDevice(tenantId, deviceId, new Device())
            .compose(ok -> {
                // now try to update the device with missing content type
                final JsonObject requestBody = new JsonObject()
                        .put(RegistryManagementConstants.FIELD_EXT, new JsonObject()
                                .put("test", "testUpdateDeviceFailsForMissingContentType")
                                .put("newKey1", "newValue1"));
                return registry.updateDevice(tenantId, deviceId, requestBody, null, HttpURLConnection.HTTP_BAD_REQUEST);
            })
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to update a device with a body that exceeds the registry's max payload limit
     * fails with a {@link HttpURLConnection#HTTP_ENTITY_TOO_LARGE} status code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForRequestPayloadExceedingLimit(final VertxTestContext ctx)  {

        registry.registerDevice(tenantId, deviceId, new Device())
            .compose(ok -> {
                final var data = new char[3000];
                Arrays.fill(data, 'x');
                final JsonObject requestBody = new JsonObject()
                        .put(RegistryManagementConstants.FIELD_EXT, new JsonObject()
                                .put("data", new String(data)));
                return registry.updateDevice(
                        tenantId,
                        deviceId,
                        requestBody,
                        "application/json",
                        HttpURLConnection.HTTP_ENTITY_TOO_LARGE);
            })
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }


    /**
     * Verifies that a device's registration information and credentials can no longer be retrieved
     * once the device has been deregistered.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceSucceeds(final VertxTestContext ctx) {

        registry.addDeviceToTenant(tenantId, deviceId, "secret")
            .compose(ok -> registry.deregisterDevice(tenantId, deviceId))
            .compose(ok -> registry.getCredentials(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that a request to deregister a device succeeds even if the given tenant does not exist (anymore).
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testDeregisterDeviceSucceedsForNonExistingTenant(final VertxTestContext ctx) {

        registry.addDeviceToTenant(tenantId, deviceId, "secret")
            .compose(ok -> registry.removeTenant(tenantId))
            .compose(ok -> registry.deregisterDevice(tenantId, deviceId, HttpURLConnection.HTTP_NO_CONTENT))
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that a request to deregister all devices of a tenant succeeds.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testDeregisterDevicesOfTenantSucceeds(final VertxTestContext ctx) {

        final String otherDeviceId = getHelper().getRandomDeviceId(tenantId);

        registry.addDeviceToTenant(tenantId, deviceId, "secret")
            .compose(ok -> registry.addDeviceToTenant(tenantId, otherDeviceId, "othersecret"))
            .onFailure(ctx::failNow)
            .compose(ok -> registry.deregisterDevicesOfTenant(tenantId))
            .onFailure(ctx::failNow)
            .compose(ok -> registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .compose(ok -> registry.getCredentials(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .compose(ok -> registry.getRegistrationInfo(tenantId, otherDeviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .compose(ok -> registry.getCredentials(tenantId, otherDeviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that a request to deregister a non-existing device fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForNonExistingDevice(final VertxTestContext ctx) {

        registry.deregisterDevice(tenantId, "non-existing-device", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                ctx.completeNow();
            }));
    }

    /**
     * Tests verifying the search devices operation.
     *
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> 
     *      Device Registry Management API - Search Devices</a>
     */
    @Nested
    @EnabledIfRegistrySupportsFeatures(searchDevices = true)
    class SearchDevicesIT {
        /**
         * Verifies that a request to search devices fails with a {@value HttpURLConnection#HTTP_NOT_FOUND}
         * when no matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesFailsWhenNoDevicesAreFound(final VertxTestContext ctx) {
            final Device device = new Device().setEnabled(false);
            final String filterJson = getFilterJson("/enabled", true, "eq");

            registry.registerDevice(tenantId, deviceId, device)
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(filterJson),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_NOT_FOUND))
                .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices fails when page size is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithInvalidPageSizeFails(final VertxTestContext ctx) {
            final int invalidPageSize = -100;

            registry.registerDevice(tenantId, deviceId)
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.of(invalidPageSize),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_BAD_REQUEST))
                .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices with pageSize succeeds and the result is in accordance
         * with the specified page size.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithValidPageSizeSucceeds(final VertxTestContext ctx) {

            final int pageSize = 1;

            Future.all(
                    registry.registerDevice(tenantId, getHelper().getRandomDeviceId(tenantId), new Device()),
                    registry.registerDevice(tenantId, getHelper().getRandomDeviceId(tenantId), new Device()))
                .compose(response -> registry.searchDevices(
                        tenantId,
                        Optional.of(pageSize),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(2);
                        assertThat(searchDevicesResult.getResult()).hasSize(1);
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search devices fails when page offset is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithInvalidPageOffsetFails(final VertxTestContext ctx) {
            final int invalidPageOffset = -100;

            registry.registerDevice(tenantId, deviceId)
                    .compose(ok -> registry.searchDevices(
                            tenantId,
                            Optional.empty(),
                            Optional.of(invalidPageOffset),
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices with page offset succeeds and the result is in accordance with
         * the specified page offset.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithValidPageOffsetSucceeds(final VertxTestContext ctx) {
            final String deviceId1 = getHelper().getRandomDeviceId(tenantId);
            final String deviceId2 = getHelper().getRandomDeviceId(tenantId);
            final Device device1 = new Device().setExtensions(Map.of("id", "aaa"));
            final Device device2 = new Device().setExtensions(Map.of("id", "bbb"));
            final int pageSize = 1;
            final int pageOffset = 1;
            final String sortJson = getSortJson("/ext/id", "desc");

            Future.all(
                    registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.of(pageSize),
                        Optional.of(pageOffset),
                        List.of(),
                        List.of(sortJson),
                        Optional.empty(),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(2);
                        assertThat(searchDevicesResult.getResult()).hasSize(1);
                        assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId1);
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search devices fails when filterJson is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithInvalidFilterJsonFails(final VertxTestContext ctx) {

            registry.registerDevice(tenantId, deviceId)
                    .compose(ok -> registry.searchDevices(
                            tenantId,
                            Optional.empty(),
                            Optional.empty(),
                            List.of("Invalid filterJson"),
                            List.of(),
                            Optional.empty(),
                            HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices with multiple filters succeeds and matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithValidMultipleFiltersSucceeds(final VertxTestContext ctx) {
            final String deviceId1 = getHelper().getRandomDeviceId(tenantId);
            final String deviceId2 = getHelper().getRandomDeviceId(tenantId);
            final Device device1 = new Device().setEnabled(false).setExtensions(Map.of("id", "1"));
            final Device device2 = new Device().setEnabled(true).setExtensions(Map.of("id", "2"));
            final String filterJson1 = getFilterJson("/ext/id", "1", "eq");
            final String filterJson2 = getFilterJson("/enabled", true, "eq");
            final String filterJson3 = getFilterJson("/enabled", false, "eq");

            Future.all(
                    registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(filterJson1, filterJson2),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_NOT_FOUND))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(filterJson1, filterJson3),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(1);
                        assertThat(searchDevicesResult.getResult()).hasSize(1);
                        assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId1);
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search devices with filters containing the wildcard character '*' 
         * succeeds and matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithWildCardToMatchMultipleCharactersSucceeds(final VertxTestContext ctx) {
            final String deviceId1 = getHelper().getRandomDeviceId(tenantId);
            final String deviceId2 = getHelper().getRandomDeviceId(tenantId);
            final Device device1 = new Device().setEnabled(false).setExtensions(Map.of("id", "$id:1"));
            final Device device2 = new Device().setEnabled(true).setExtensions(Map.of("id", "$id:2"));
            final String filterJson1 = getFilterJson("/enabled", true, "eq");
            final String filterJson2 = getFilterJson("/ext/id", "$id*", "eq");

            Future.all(
                    registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(filterJson1, filterJson2),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(1);
                        assertThat(searchDevicesResult.getResult()).hasSize(1);
                        assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId2);
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search gateway devices succeeds and matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchGatewayDevicesSucceeds(final VertxTestContext ctx) {
            final String deviceId1 = "1_" + getHelper().getRandomDeviceId(tenantId);
            final String deviceId2 = "2_" + getHelper().getRandomDeviceId(tenantId);
            final String deviceId3 = "3_" + getHelper().getRandomDeviceId(tenantId);
            final Device device1 = new Device().setMemberOf(List.of("gwGroup1"));
            final Device device2 = new Device().setVia(List.of(deviceId3));
            final Device device3 = new Device();

            Future.all(
                    registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2),
                    registry.registerDevice(tenantId, deviceId3, device3))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        Optional.of(true),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(2);
                        assertThat(searchDevicesResult.getResult()).hasSize(2);
                        assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId1);
                        assertThat(searchDevicesResult.getResult().get(1).getId()).isEqualTo(deviceId3);
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search devices with a filter containing the wildcard character '*' fails with a
         * {@value HttpURLConnection#HTTP_NOT_FOUND} as no matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithWildCardToMatchMultipleCharactersFails(final VertxTestContext ctx) {
            final String deviceId = getHelper().getRandomDeviceId(tenantId);
            final Device device = new Device().setExtensions(Map.of("id", "$id:1"));
            final String filterJson = getFilterJson("/ext/id", "*id*2", "eq");

            registry.registerDevice(tenantId, deviceId, device)
                    .compose(ok -> registry.searchDevices(
                            tenantId,
                            Optional.empty(),
                            Optional.empty(),
                            List.of(filterJson),
                            List.of(),
                            Optional.empty(),
                            HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices with filters containing the wildcard character '?' 
         * succeeds and matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithWildCardToMatchExactlyOneCharacterSucceeds(final VertxTestContext ctx) {
            final String deviceId1 = getHelper().getRandomDeviceId(tenantId);
            final String deviceId2 = getHelper().getRandomDeviceId(tenantId);
            final Device device1 = new Device().setEnabled(false).setExtensions(Map.of("id", "$id:1"));
            final Device device2 = new Device().setEnabled(true).setExtensions(Map.of("id", "$id:2"));
            final String filterJson1 = getFilterJson("/enabled", true, "eq");
            final String filterJson2 = getFilterJson("/ext/id", "$id?2", "eq");

            Future.all(
                    registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(filterJson1, filterJson2),
                        List.of(),
                        Optional.empty(),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(1);
                        assertThat(searchDevicesResult.getResult()).hasSize(1);
                        assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId2);
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search devices with a filter containing the wildcard character '?' fails with a
         * {@value HttpURLConnection#HTTP_NOT_FOUND} as no matching devices are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithWildCardToMatchExactlyOneCharacterFails(final VertxTestContext ctx) {
            final String deviceId = getHelper().getRandomDeviceId(tenantId);
            final Device device = new Device().setExtensions(Map.of("id", "$id:2"));
            final String filterJson = getFilterJson("/ext/id", "$id:?2", "eq");

            registry.registerDevice(tenantId, deviceId, device)
                    .compose(ok -> registry.searchDevices(
                            tenantId,
                            Optional.empty(),
                            Optional.empty(),
                            List.of(filterJson),
                            List.of(),
                            Optional.empty(),
                            HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices fails when sortJson is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithInvalidSortJsonFails(final VertxTestContext ctx) {

            registry.registerDevice(tenantId, deviceId)
                    .compose(ok -> registry.searchDevices(
                            tenantId,
                            Optional.empty(),
                            Optional.empty(),
                            List.of(),
                            List.of("Invalid sortJson"),
                            Optional.empty(),
                            HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search devices with a valid sort option succeeds and the result is sorted
         * accordingly.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithValidSortOptionSucceeds(final VertxTestContext ctx) {
            final String deviceId1 = getHelper().getRandomDeviceId(tenantId);
            final String deviceId2 = getHelper().getRandomDeviceId(tenantId);
            final Device device1 = new Device().setExtensions(Map.of("id", "aaa"));
            final Device device2 = new Device().setExtensions(Map.of("id", "bbb"));
            final String sortJson = getSortJson("/ext/id", "desc");

            Future.all(
                    registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                .compose(ok -> registry.searchDevices(
                        tenantId,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(sortJson),
                        Optional.empty(),
                        HttpURLConnection.HTTP_OK))
                .onComplete(ctx.succeeding(httpResponse -> {
                    ctx.verify(() -> {
                        final SearchResult<DeviceWithId> searchDevicesResult = JacksonCodec
                                .decodeValue(httpResponse.body(), new TypeReference<>() { });
                        assertThat(searchDevicesResult.getTotal()).isEqualTo(2);
                        assertThat(searchDevicesResult.getResult()).hasSize(2);
                        assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId2);
                        assertThat(searchDevicesResult.getResult().get(1).getId()).isEqualTo(deviceId1);
                    });
                    ctx.completeNow();
                }));
        }

        private <T> String getFilterJson(final String field, final T value, final String operator) {
            final JsonObject filterJson = new JsonObject()
                    .put(RegistryManagementConstants.FIELD_FILTER_FIELD, field)
                    .put(RegistryManagementConstants.FIELD_FILTER_VALUE, value);
            Optional.ofNullable(operator)
                    .ifPresent(op -> filterJson.put(RegistryManagementConstants.FIELD_FILTER_OPERATOR, op));

            return filterJson.toString();
        }

        private String getSortJson(final String field, final String direction) {
            final JsonObject sortJson = new JsonObject().put(RegistryManagementConstants.FIELD_FILTER_FIELD, field);
            Optional.ofNullable(direction)
                    .ifPresent(dir -> sortJson.put(RegistryManagementConstants.FIELD_SORT_DIRECTION, dir));

            return sortJson.toString();
        }
    }

    private static String assertLocationHeader(final MultiMap responseHeaders, final String tenantId) {
        final String location = responseHeaders.get(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        final Pattern pattern = Pattern.compile("/(.*)/(.*)/(.*)/(.*)");
        final Matcher matcher = pattern.matcher(location);
        assertThat(matcher.matches()).isTrue();
        assertThat(matcher.group(2)).isEqualTo(RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);
        assertThat(matcher.group(3)).isEqualTo(tenantId);
        final String generatedId = matcher.group(4);
        assertThat(generatedId).isNotNull();
        return generatedId;
    }

    private static DeviceStatus getDeviceStatus(final JsonObject deviceData) {
        final var deviceStatus = deviceData.getJsonObject(RegistryManagementConstants.FIELD_STATUS);
        return Optional.ofNullable(deviceStatus)
                .map(statusJson -> statusJson.mapTo(DeviceStatus.class))
                .orElse(null);
    }

    private static void assertRegistrationInformation(
            final HttpResponse<Buffer> response,
            final Device expectedData,
            final Instant expectedCreationTime,
            final boolean updatedOnExpectedToBeNonNull) {

        final JsonObject actualDeviceJson = response.bodyAsJsonObject();
        final Device actualDevice = actualDeviceJson.mapTo(Device.class);

        Assertions.assertThat(actualDevice)
                .usingRecursiveComparison()
                .isEqualTo(expectedData);

        // internal status is not decoded from JSON as users should not be allowed to change internal status
        final JsonObject actualDeviceStatus = actualDeviceJson.getJsonObject(RegistryManagementConstants.FIELD_STATUS);
        assertWithMessage("response containing %s property or the property value",
                RegistryManagementConstants.FIELD_AUTO_PROVISIONED)
                .that(actualDeviceStatus.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED, Boolean.FALSE))
                .isFalse();
        assertWithMessage("response containing %s property or the property value",
                RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)
                .that(actualDeviceStatus.getBoolean(
                        RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT, Boolean.FALSE))
                .isFalse();

        final Instant creationTime = actualDeviceStatus.getInstant(RegistryManagementConstants.FIELD_STATUS_CREATION_DATE);
        if (expectedCreationTime == null) {
            assertWithMessage("device creation time").that(creationTime).isNotNull();
        } else {
            assertWithMessage("device creation time").that(creationTime).isEqualTo(expectedCreationTime);
        }

        final Instant lastUpdatedAt = actualDeviceStatus.getInstant(RegistryManagementConstants.FIELD_STATUS_LAST_UPDATE);
        if (updatedOnExpectedToBeNonNull) {
            assertWithMessage("device updated time").that(lastUpdatedAt).isAtLeast(creationTime);
        } else {
            assertWithMessage("device updated time").that(lastUpdatedAt).isNull();
        }
    }
}
