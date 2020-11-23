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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.SearchDevicesResult;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
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

        registry
                .addTenant(tenantId)
                .onComplete(ctx.completing());

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

        registry.registerDevice(tenantId, deviceId, device)
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
     * Verifies that a request to register a device with an invalid device identifier fails.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForInvalidDeviceId(final VertxTestContext ctx) {

        registry.registerDevice(tenantId, "device ID With spaces and $pecial chars!", null, HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that a request to register a device with an invalid tenant identifier fails.
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
                ctx.verify(() -> assertRegistrationInformation(httpResponse, device));
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
                    ctx.verify(() -> assertRegistrationInformation(httpResponse, updatedData.mapTo(Device.class)));
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

    /**
     * Tests verifying the search devices operation.
     *
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> 
     *      Device Registry Management API - Search Devices</a>
     */
    @Nested
    @EnabledIfSystemProperty(named = "deviceregistry.supportsSearchDevices", matches = "true")
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
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.completing());
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
                    .compose(ok -> registry.searchDevices(tenantId, Optional.of(invalidPageSize), Optional.empty(),
                            List.of(), List.of(), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.completing());
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

            CompositeFuture.all(
                    registry.registerDevice(tenantId, getHelper().getRandomDeviceId(tenantId), new Device()),
                    registry.registerDevice(tenantId, getHelper().getRandomDeviceId(tenantId), new Device())
                            .compose(ok -> registry.searchDevices(tenantId, Optional.of(pageSize), Optional.empty(),
                                    List.of(), List.of(), HttpURLConnection.HTTP_OK))
                            .onComplete(ctx.succeeding(httpResponse -> {
                                ctx.verify(() -> {
                                    final SearchDevicesResult searchDevicesResult = httpResponse
                                            .bodyAsJson(SearchDevicesResult.class);
                                    assertThat(searchDevicesResult.getTotal()).isEqualTo(2);
                                    assertThat(searchDevicesResult.getResult()).hasSize(1);
                                });
                                ctx.completeNow();
                            })));
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
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.of(invalidPageOffset),
                            List.of(), List.of(), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.completing());
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

            CompositeFuture.all(registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                    .compose(ok -> registry.searchDevices(tenantId, Optional.of(pageSize), Optional.of(pageOffset),
                            List.of(), List.of(sortJson), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchDevicesResult searchDevicesResult = httpResponse
                                    .bodyAsJson(SearchDevicesResult.class);
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
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of("Invalid filterJson"), List.of(), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.completing());
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

            CompositeFuture
                    .all(registry.registerDevice(tenantId, deviceId1, device1),
                            registry.registerDevice(tenantId, deviceId2, device2))
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson2), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson3), List.of(), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchDevicesResult searchDevicesResult = httpResponse
                                    .bodyAsJson(SearchDevicesResult.class);
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

            CompositeFuture
                    .all(registry.registerDevice(tenantId, deviceId1, device1),
                            registry.registerDevice(tenantId, deviceId2, device2))
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson2), List.of(), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchDevicesResult searchDevicesResult = httpResponse
                                    .bodyAsJson(SearchDevicesResult.class);
                            assertThat(searchDevicesResult.getTotal()).isEqualTo(1);
                            assertThat(searchDevicesResult.getResult()).hasSize(1);
                            assertThat(searchDevicesResult.getResult().get(0).getId()).isEqualTo(deviceId2);
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
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.completing());
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

            CompositeFuture
                    .all(registry.registerDevice(tenantId, deviceId1, device1),
                            registry.registerDevice(tenantId, deviceId2, device2))
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson2), List.of(), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchDevicesResult searchDevicesResult = httpResponse
                                    .bodyAsJson(SearchDevicesResult.class);
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
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(),
                            List.of(filterJson), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.completing());
        }

        /**
         * Verifies that a request to search devices fails when sortJson is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchDevicesWithInvalidSortJsonFails(final VertxTestContext ctx) {

            registry.registerDevice(tenantId, deviceId)
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(), List.of(),
                            List.of("Invalid sortJson"), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.completing());
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

            CompositeFuture.all(registry.registerDevice(tenantId, deviceId1, device1),
                    registry.registerDevice(tenantId, deviceId2, device2))
                    .compose(ok -> registry.searchDevices(tenantId, Optional.empty(), Optional.empty(), List.of(),
                            List.of(sortJson), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchDevicesResult searchDevicesResult = httpResponse
                                    .bodyAsJson(SearchDevicesResult.class);
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
        assertThat(matcher.matches());
        assertThat(matcher.group(2)).isEqualTo(RegistryManagementConstants.DEVICES_HTTP_ENDPOINT);
        assertThat(matcher.group(3)).isEqualTo(tenantId);
        final String generatedId = matcher.group(4);
        assertThat(generatedId).isNotNull();
        return generatedId;
    }

    private static void assertRegistrationInformation(final HttpResponse<Buffer> response, final Device expectedData) {

        final JsonObject actualDeviceJson = response.bodyAsJsonObject();

        // internal status is not decoded from JSON as users should not be allowed to change internal status
        final JsonObject actualDeviceStatus = actualDeviceJson.getJsonObject(RegistryManagementConstants.FIELD_STATUS);
        assertThat(actualDeviceStatus.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONED)).isFalse();
        assertThat(actualDeviceStatus.getBoolean(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)).isFalse();

        final Device actualDevice = actualDeviceJson.mapTo(Device.class);
        final Comparator<Instant> close = (Instant d1, Instant d2) -> d1.compareTo(d2) < 1000 ? 0 : 1;

        assertThat(actualDevice)
                .usingRecursiveComparison()
                .withComparatorForFields(close, "status.creationTime", "status.lastUpdate")
                .isEqualTo(expectedData);
    }
}
