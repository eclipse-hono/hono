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
package org.eclipse.hono.service.registration;

import io.opentracing.noop.NoopSpan;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract class used as a base for verifying behavior of {@link RegistrationService} and the
 * {@link DeviceManagementService} in device registry implementations.
 *
 */
public abstract class AbstractRegistrationServiceTest {

    /**
     * The tenant used in tests.
     */
    protected static final String TENANT = Constants.DEFAULT_TENANT;
    /**
     * The device identifier used in tests.
     */
    protected static final String DEVICE = "4711";
    /**
     * The gateway identifier used in the tests.
     */
    protected static final String GW = "gw-1";

    /**
     * Gets registration service being tested.
     * @return The registration service
     */
    public abstract RegistrationService getRegistrationService();

    /**
     * Gets device management service being tested.
     * 
     * @return The device management service
     */
    public abstract DeviceManagementService getDeviceManagementService();

    /**
     * Verifies that the registry returns 404 when getting an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetUnknownDeviceReturnsNotFound(final VertxTestContext ctx) {

        getDeviceManagementService()
                .readDevice(TENANT, DEVICE, NoopSpan.INSTANCE, ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterUnknownDeviceReturnsNotFound(final VertxTestContext ctx) {

        getDeviceManagementService()
                .deleteDevice(TENANT, DEVICE, Optional.empty(), NoopSpan.INSTANCE, ctx.succeeding(response -> ctx.verify(() -> {
                            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                            ctx.completeNow();
                        })));
    }

    /**
     * Verifies that the registry returns 409 when trying to register a device twice.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDuplicateRegistrationFails(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(ok -> {
            final Future<OperationResult<Id>> addResult = Future.future();
            getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, addResult);
            return addResult;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetSucceedsForRegisteredDevice(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> addResult = Future.future();

        getDeviceManagementService()
                .createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, addResult);

        addResult
                .map(r -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus());
                }))
                .compose(ok -> {
                    final Future<OperationResult<Device>> getResult = Future.future();
                    getDeviceManagementService().readDevice(TENANT, DEVICE, NoopSpan.INSTANCE, getResult);
                    return getResult;
                })
                .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                    assertNotNull(s.getPayload());

                    getRegistrationService().assertRegistration(TENANT, DEVICE, ctx.succeeding(s2 -> {
                        assertEquals(HttpURLConnection.HTTP_OK, s2.getStatus());
                        assertNotNull(s2.getPayload());
                        ctx.completeNow();
                    }));

                })));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetSucceedsForRegisteredDeviceWithData(final VertxTestContext ctx) {

        final List<String> vias = Collections.unmodifiableList(Arrays.asList("a", "b", "c"));
        final String deviceId = UUID.randomUUID().toString();
        final Device device = new Device();
        device.setVia(vias);

        final Future<OperationResult<Id>> addResult = Future.future();

        getDeviceManagementService()
                .createDevice(TENANT, Optional.of(deviceId), device, NoopSpan.INSTANCE, addResult);

        addResult
                .map(r -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus());
                }))
                .compose(ok -> {
                    final Future<OperationResult<Device>> getResult = Future.future();
                    getDeviceManagementService().readDevice(TENANT, deviceId, NoopSpan.INSTANCE, getResult);
                    return getResult;
                })
                .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                    assertNotNull(s.getPayload());
                    assertEquals(vias, s.getPayload().getVia());

                    getRegistrationService().assertRegistration(TENANT, deviceId, ctx.succeeding(s2 -> {
                        assertEquals(HttpURLConnection.HTTP_OK, s2.getStatus());
                        assertNotNull(s2.getPayload());

                        // assert "via"
                        final JsonArray viaJson = s2.getPayload().getJsonArray("via");
                        assertNotNull(viaJson);
                        assertEquals(vias, viaJson.stream().map(Object::toString).collect(Collectors.toList()));

                        ctx.completeNow();
                    }));

                })));
    }

    /**
     * Verifies that the registry returns a copy of the registered device information on each invocation of the get
     * operation..
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetReturnsCopyOfOriginalData(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> addResult = Future.future();
        final Future<OperationResult<Device>> getResult = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, addResult);
        addResult
                .compose(r -> {
                    ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus()));
                    getDeviceManagementService().readDevice(TENANT, DEVICE, NoopSpan.INSTANCE, getResult);
                    return getResult;
                })
                .compose(r -> {
                    ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_OK, r.getStatus()));
                    r.getPayload().setExtensions(new HashMap<>());
                    r.getPayload().getExtensions().put("new-prop", true);

                    final Future<OperationResult<Device>> secondGetResult = Future.future();
                    getDeviceManagementService().readDevice(TENANT, DEVICE, NoopSpan.INSTANCE, secondGetResult);
                    return secondGetResult;
                })
                .setHandler(ctx.succeeding(secondGetResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_OK, secondGetResult.getStatus());
                        assertNotNull(getResult.result().getPayload().getExtensions().get("new-prop"));
                        assertNotEquals(getResult.result().getPayload(), secondGetResult.getPayload());
                        assertNotNull(secondGetResult.getPayload());
                        assertNotNull(secondGetResult.getPayload().getExtensions());
                        assertTrue(secondGetResult.getPayload().getExtensions().isEmpty());
                        ctx.completeNow();
                    });
                }));
    }

    /**
     * Verifies that the registry returns 404 when getting an unregistered device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForDeregisteredDevice(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint get = ctx.checkpoint(3);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.compose(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            get.flag();
            final Future<Result<Void>> deregisterResult = Future.future();
            getDeviceManagementService().deleteDevice(TENANT, DEVICE, Optional.empty(), NoopSpan.INSTANCE, deregisterResult);
            return deregisterResult;
        }).compose(response -> {
            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
            get.flag();
            final Future<OperationResult<Device>> getResult = Future.future();
            getDeviceManagementService().readDevice(TENANT, DEVICE, NoopSpan.INSTANCE, getResult);
            return getResult;
        }).setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
            get.flag();
        })));
    }

    /**
     * Verify that registering a device without a device ID successfully creates a device
     * assigned with a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedForMissingDeviceId(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.empty(), new Device(), NoopSpan.INSTANCE, result);
        result.setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            final String deviceId = response.getPayload().getId();
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            assertNotNull(deviceId);
            ctx.completeNow();
        })));
    }

    /**
     * Verify that registering a device without a device ID successfully creates a device
     * assigned with a device ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddSucceedAndContainsResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            final String deviceId = response.getPayload().getId();
            final String resourceVersion = response.getResourceVersion().orElse(null);
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            assertEquals(DEVICE, deviceId);
            assertNotNull(resourceVersion);
            ctx.completeNow();
        })));
    }

    /**
     * Verify that updating a device fails when the request contain a non matching resourceVersion.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateFailsWithInvalidResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<OperationResult<Id>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);

            getDeviceManagementService().updateDevice(
                    TENANT, DEVICE,
                    new JsonObject().put("ext", new JsonObject().put("customKey", "customValue")).mapTo(Device.class),
                    Optional.of(resourceVersion + "abc"), NoopSpan.INSTANCE,
                    update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that updating a device succeed when the request contain an empty resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateSucceedWithMissingResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<OperationResult<Id>> update = Future.future();

            getDeviceManagementService().updateDevice(
                    TENANT, DEVICE,
                    new JsonObject().put("ext", new JsonObject().put("customKey", "customValue")).mapTo(Device.class),
                    Optional.empty(), NoopSpan.INSTANCE,
                    update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that updating a device succeeds when the request contain the matching resourceVersion.
     * Also verify that a new resourceVersion is returned with the update result.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateSucceedWithCorrectResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);
        final JsonObject version = new JsonObject();

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<OperationResult<Id>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);
            version.put("1", resourceVersion);

            getDeviceManagementService().updateDevice(
                    TENANT, DEVICE,
                    new JsonObject().put("ext", new JsonObject().put("customKey", "customValue")).mapTo(Device.class),
                    Optional.of(resourceVersion), NoopSpan.INSTANCE,
                    update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    final String secondResourceVersion = response.getResourceVersion().orElse(null);

                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    assertNotEquals(secondResourceVersion, version.getString("1"));
                    register.flag();
                })));
    }

    /**
     * Verify that deleting a device succeeds when the request contain an empty resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteSucceedWithMissingResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<Result<Void>> update = Future.future();

            getDeviceManagementService().deleteDevice(
                    TENANT, DEVICE, Optional.empty(), NoopSpan.INSTANCE, update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that deleting a device succeeds when the request contain the matching resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteSucceedWithCorrectResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<Result<Void>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);

            getDeviceManagementService().deleteDevice(
                    TENANT, DEVICE, Optional.of(resourceVersion), NoopSpan.INSTANCE, update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verify that deleting a device succeeds when the request contain the matching resourceVersion parameter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeleteFailsWithInvalidResourceVersion(final VertxTestContext ctx) {

        final Future<OperationResult<Id>> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getDeviceManagementService().createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE, result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(rr -> {
            final Future<Result<Void>> update = Future.future();
            final String resourceVersion = rr.getResourceVersion().orElse(null);

            getDeviceManagementService().deleteDevice(
                    TENANT, DEVICE, Optional.of(resourceVersion+10), NoopSpan.INSTANCE, update);
            return update;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, response.getStatus());
                    register.flag();
                })));
    }


    /**
     * Asserts that a device is registered.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A succeeded future if the device is registered.
     */
    protected final Future<?> assertCanReadDevice(final String tenantId, final String deviceId) {
        final Future<OperationResult<Device>> result = Future.future();
        getDeviceManagementService().readDevice(tenantId, deviceId, NoopSpan.INSTANCE, result);
        return result.map(r -> {
            if (r.getStatus() == HttpURLConnection.HTTP_OK) {
                return r;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
            }
        });
    }

    /**
     * Helps asserting device data.
     * 
     * @param tenant The tenant.
     * @param deviceId The device ID.
     * @param gatewayId The optional gateway ID
     * @param managementAssertions assertions for the management data.
     * @param adapterAssertions assertions for the adapter data.
     * @return A new future that will succeed when the read/get operations succeed and the assertions are valid.
     *         Otherwise the future must fail.
     */
    protected Future<?> assertDevice(final String tenant, final String deviceId, final Optional<String> gatewayId,
            final Handler<OperationResult<Device>> managementAssertions,
            final Handler<RegistrationResult> adapterAssertions) {

        // read management data

        final Future<OperationResult<Device>> f1 = Future.future();
        getDeviceManagementService().readDevice(tenant, deviceId, NoopSpan.INSTANCE, f1);

        // read adapter data

        final Future<RegistrationResult> f2 = Future.future();
        if (gatewayId.isPresent()) {
            getRegistrationService().assertRegistration(tenant, deviceId, gatewayId.get(), f2);
        } else {
            getRegistrationService().assertRegistration(tenant, deviceId, f2);
        }

        return CompositeFuture.all(
                f1.map(r -> {
                    managementAssertions.handle(r);
                    return null;
                }),
                f2.map(r -> {
                    adapterAssertions.handle(r);
                    return null;
                }));
    }

    /**
     * Assert devices, expecting them to be "not found".
     * 
     * @param devices The map of devices to assert.
     * @return A future, reporting the assertion status.
     */
    protected Future<?> assertDevicesNotFound(final Map<String, Device> devices) {

        Future<?> current = Future.succeededFuture();

        for (final Map.Entry<String, Device> entry : devices.entrySet()) {
            current = current.compose(ok -> assertDevice(TENANT, entry.getKey(), Optional.empty(),
                    r -> {
                        assertEquals(HTTP_NOT_FOUND, r.getStatus());
                    },
                    r -> {
                        assertEquals(HTTP_NOT_FOUND, r.getStatus());
                    }));
        }

        return current;

    }

    /**
     * Assert a set of devices.
     * <p>
     * This will read the devices and expect them to be found and match the provided device information.
     * 
     * @param devices The devices and device information.
     * @return A future, reporting the assertion status.
     */
    protected Future<?> assertDevices(final Map<String, Device> devices) {

        Future<?> current = Future.succeededFuture();

        for (final Map.Entry<String, Device> entry : devices.entrySet()) {
            final var device = entry.getValue();
            current = current.compose(ok -> assertDevice(TENANT, entry.getKey(), Optional.empty(),
                    r -> {
                        assertEquals(HTTP_OK, r.getStatus());
                        assertNotNull(r.getPayload());
                        assertNotNull(r.getResourceVersion()); // may be empty, but not null
                        assertNotNull(r.getCacheDirective()); // may be empty, but not null
                        assertEquals(device.getEnabled(), r.getPayload().getEnabled());
                        assertEquals(device.getVia(), r.getPayload().getVia());
                    },
                    r -> {
                        if (Boolean.FALSE.equals(device.getEnabled())) {
                            assertEquals(HTTP_NOT_FOUND, r.getStatus());
                            assertNull(r.getPayload());
                        } else {
                            assertEquals(HTTP_OK, r.getStatus());
                            assertNotNull(r.getPayload());
                            final var actualVias = r.getPayload().getJsonArray("via", new JsonArray());
                            assertIterableEquals(device.getVia(), actualVias);
                        }

                    }));
        }

        return current;

    }

    /**
     * Create a set of devices.
     * <p>
     * Devices create operations must report "OK" in order to succeed.
     * 
     * @param devices The devices to create.
     * @return A future, tracking the creation process.
     */
    protected Future<?> createDevices(final Map<String, Device> devices) {

        Future<?> current = Future.succeededFuture();
        for (final Map.Entry<String, Device> entry : devices.entrySet()) {

            current = current.compose(ok -> {
                final Future<OperationResult<Id>> f = Future.future();
                getDeviceManagementService().createDevice(TENANT, Optional.of(entry.getKey()), entry.getValue(), NoopSpan.INSTANCE, f);
                return f.map(r -> {
                    assertEquals(HTTP_CREATED, r.getStatus());
                    return null;
                });
            });

        }

        return current;

    }
}
