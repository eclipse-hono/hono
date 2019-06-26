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

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.eclipse.hono.util.Constants.JSON_FIELD_DEVICE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link CompleteBaseRegistrationService}.
 *
 *
 */
@ExtendWith(VertxExtension.class)
public class CompleteBaseRegistrationServiceTest {

    /**
     * Verifies that the addDevice method returns not implemented.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAddDevice(final VertxTestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationServiceWithoutImpls();

        // WHEN trying to add a new device
        registrationService.addDevice(Constants.DEFAULT_TENANT, "4711", new JsonObject(), ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the request fails with status code 501.
            assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, result.getStatus());
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the updateDevice method returns not implemented.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testUpdateDevice(final VertxTestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationServiceWithoutImpls();

        // WHEN trying to update a device
        registrationService.updateDevice(Constants.DEFAULT_TENANT, "4711", new JsonObject(), ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the request fails with status code 501.
            assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, result.getStatus());
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the removeDevice method returns not implemented.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testRemoveDevice(final VertxTestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationServiceWithoutImpls();

        // WHEN trying to remove a device
        registrationService.removeDevice(Constants.DEFAULT_TENANT, "4711", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the request fails with status code 501.
            assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, result.getStatus());
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the registry returns 400 when issuing a request with an unsupported action.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProcessRequestFailsWithUnsupportedAction(final VertxTestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationServiceWithoutImpls();

        registrationService
                .processRequest(EventBusMessage.forOperation("unknown-action"))
                .setHandler(ctx.failing(t -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the updateDeviceLastVia method updates the 'last-via' property.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testUpdateDeviceLastVia(final VertxTestContext ctx) {

        // GIVEN a registry that supports updating registration information
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationService();

        // WHEN trying to update the 'last-via' property
        final Future<Void> updateLastViaFuture = registrationService.updateDeviceLastVia(Constants.DEFAULT_TENANT, "4714", "gw-1", new JsonObject());
        updateLastViaFuture.setHandler(ctx.succeeding(result -> {
            // THEN the device data contains a 'last-via' property
            registrationService.getDevice(Constants.DEFAULT_TENANT, "4714", ctx.succeeding(getDeviceResult -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, getDeviceResult.getStatus());
                assertNotNull(getDeviceResult.getPayload(), "payload not set");
                final JsonObject data = getDeviceResult.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA);
                assertNotNull(data, "payload data not set");
                final JsonObject lastViaObj = data.getJsonObject(RegistrationConstants.FIELD_LAST_VIA);
                assertNotNull(lastViaObj, RegistrationConstants.FIELD_LAST_VIA + " property not set");
                assertEquals("gw-1", lastViaObj.getString(JSON_FIELD_DEVICE_ID));
                assertNotNull(lastViaObj.getString(RegistrationConstants.FIELD_LAST_VIA_UPDATE_DATE),
                        RegistrationConstants.FIELD_LAST_VIA_UPDATE_DATE + " property not set");
                ctx.completeNow();
            })));
        }));
    }

    /**
     * Returns a CompleteBaseRegistrationService without implementations of the get/add/update/remove methods.
     * 
     * @return CompleteBaseRegistrationService instance.
     */
    private CompleteBaseRegistrationService<ServiceConfigProperties> newCompleteRegistrationServiceWithoutImpls() {

        return new CompleteBaseRegistrationService<>() {

            @Override
            protected String getEventBusAddress() {
                return "requests.in";
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }
        };
    }

    private CompleteBaseRegistrationService<ServiceConfigProperties> newCompleteRegistrationService() {
        return newCompleteRegistrationService(this::getDevice);
    }

    private CompleteBaseRegistrationService<ServiceConfigProperties> newCompleteRegistrationService(final Function<String, Future<RegistrationResult>> devices) {

        return new CompleteBaseRegistrationService<>() {

            private final Map<String, JsonObject> updatedDevicesMap = new HashMap<>();

            @Override
            protected String getEventBusAddress() {
                return "requests.in";
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

            @Override
            public void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
                if (updatedDevicesMap.containsKey(deviceId)) {
                    resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK,
                            BaseRegistrationService.getResultPayload(deviceId, updatedDevicesMap.get(deviceId)))));
                } else {
                    devices.apply(deviceId).setHandler(resultHandler);
                }
            }

            @Override
            public void updateDevice(final String tenantId, final String deviceId, final JsonObject otherKeys,
                    final Handler<AsyncResult<RegistrationResult>> resultHandler) {
                updatedDevicesMap.put(deviceId, otherKeys);
                resultHandler.handle(Future.succeededFuture(RegistrationResult.from(HTTP_NO_CONTENT)));
            }
        };
    }

    private Future<RegistrationResult> getDevice(final String deviceId) {

        if ("4711".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "4711",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                            .put(RegistrationConstants.FIELD_VIA, "gw-1"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4712".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "4712",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4713".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "4713",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_VIA, "gw-3"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4714".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "4714",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                            .put(RegistrationConstants.FIELD_VIA, new JsonArray().add("gw-1").add("gw-4")));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-1".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "gw-1",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, true));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-2".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "gw-2",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, true));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-3".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "gw-3",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-4".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "gw-4",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, true));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else {
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
        }
    }
}
