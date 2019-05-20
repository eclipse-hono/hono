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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.net.HttpURLConnection;
import java.util.function.Function;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link BaseRegistrationService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class BaseRegistrationServiceTest {

    /**
     * Verifies that a disabled device's status cannot be asserted.
     * 
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDisabledDevice(final VertxTestContext ctx) {

        // GIVEN a registry that contains a disabled device
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4712", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 404
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
            //  and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a non existing device's status cannot be asserted.
     * 
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForNonExistingDevice(final VertxTestContext ctx) {

        // GIVEN a registry that does not contain any devices
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        // WHEN trying to assert a device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "non-existent", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 404
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
            //  and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a device's status cannot be asserted by a non-existing gateway.
     * 
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForNonExistingGateway(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device but no gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status for a gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", "non-existent-gw", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 403 status
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
            //  and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a device's status cannot be asserted by a disabled gateway.
     * 
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDisabledGateway(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device
        // and a gateway that the device is configured for but
        // which is disabled
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status for a gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4713", "gw-3", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 403 status
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
            //  and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a device's status cannot be asserted by a gateway that does not
     * match the device's configured gateway.
     * 
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForWrongGateway(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device and two gateways:
        // 1. the gateway that the device is configured for.
        // 2. another gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status for the wrong gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", "gw-2", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 403 status
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
            // and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the getDevice method returns "not implemented" if the base
     * {@link BaseRegistrationService#getDevice(String, String, Handler)} implementation is used.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testGetDeviceBaseImpl(final VertxTestContext ctx) {

        // GIVEN an empty registry (using the BaseRegistrationService#getDevice(String, String, Handler) implementation)
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationServiceWithoutImpls();

        // WHEN trying to get a device's data
        registrationService.getDevice(Constants.DEFAULT_TENANT, "4711", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a status code 501
            assertEquals(HttpURLConnection.HTTP_NOT_IMPLEMENTED, result.getStatus());
            // and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the status of an enabled device with a 'via' property can be asserted successfully.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationSucceedsForDeviceWithViaProperty(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device with a default content type set
        final BaseRegistrationService<ServiceConfigProperties> registrationService = spy(newRegistrationService());

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", ctx.succeeding(result -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            assertFalse(result.getCacheDirective().isCachingAllowed());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);

            // THEN the response contains the registered default content type
            final JsonObject defaults = payload.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS);
            assertNotNull(defaults);
            assertEquals("application/default", defaults.getString(MessageHelper.SYS_PROPERTY_CONTENT_TYPE));
            // and the device data 'last-via' property was updated
            final ArgumentCaptor<String> gatewayCaptor = ArgumentCaptor.forClass(String.class);
            verify(registrationService).updateDeviceLastVia(anyString(), anyString(), gatewayCaptor.capture(), any(JsonObject.class));
            assertEquals("4711", gatewayCaptor.getValue());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a device's status can be asserted by any existing gateway
     * it has been configured to connect via.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationSucceedsForExistingGateway(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device that is configured to
        // connect via any of two enabled gateways.
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        final Checkpoint assertion = ctx.checkpoint(2);

        // WHEN trying to assert the device's registration status for gateway 1
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714", "gw-1", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            assertFalse(result.getCacheDirective().isCachingAllowed());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);
            assertion.flag();
        })));

        // WHEN trying to assert the device's registration status for gateway 4
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714", "gw-4", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            assertFalse(result.getCacheDirective().isCachingAllowed());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);
            assertion.flag();
        })));
    }

    /**
     * Verifies that the <em>assertRegistration</em> operation on a device with multiple 'via' entries updates
     * the 'last-via' property.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationUpdatesLastViaProperty(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device that is configured to
        // be connected to an enabled gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = spy(newRegistrationService());

        // WHEN trying to assert the device's registration status for gateway 1
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714", "gw-1", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            assertFalse(result.getCacheDirective().isCachingAllowed());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);

            // and the device data 'last-via' property was updated
            verify(registrationService).updateDeviceLastVia(
                    eq(Constants.DEFAULT_TENANT),
                    eq("4714"),
                    eq("gw-1"),
                    any(JsonObject.class));
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>assertRegistration</em> operation on a device with multiple 'via' entries updates
     * the 'last-via' property - here in the case where no gateway id is passed to the assertRegistration method.
     *
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationWithoutGatewayUpdatesLastViaProperty(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device that is configured to
        // be connected to an enabled gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = spy(newRegistrationService());

        final Checkpoint assertion = ctx.checkpoint(1);

        // WHEN trying to assert the device's registration status for gateway 1
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714",  ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            assertFalse(result.getCacheDirective().isCachingAllowed());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);

            // and the device data 'last-via' property was updated
            final ArgumentCaptor<String> gatewayCaptor = ArgumentCaptor.forClass(String.class);
            verify(registrationService).updateDeviceLastVia(anyString(), anyString(), gatewayCaptor.capture(), any(JsonObject.class));
            assertEquals("4714", gatewayCaptor.getValue());
            assertion.flag();
        })));
    }

    /**
     * Create a new BaseRegistrationService instance using the {@link #getDevice(String)} method to retrieve device data.
     *
     * @return New BaseRegistrationService instance.
     */
    private BaseRegistrationService<ServiceConfigProperties> newRegistrationService() {
        return newRegistrationService(this::getDevice);
    }

    private BaseRegistrationService<ServiceConfigProperties> newRegistrationService(final Function<String, Future<RegistrationResult>> devices) {

        return new BaseRegistrationService<>() {

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
                devices.apply(deviceId).setHandler(resultHandler);
            }

            @Override
            protected Future<Void> updateDeviceLastVia(final String tenantId, final String deviceId, final String gatewayId,
                    final JsonObject deviceData) {
                return Future.succeededFuture();
            }
        };
    }

    /**
     * Create a BaseRegistrationService where the <em>getDevice</em> method is not overridden and the
     * <em>updateDeviceLastVia</em> method returns a failed Future with a <em>HTTP_NOT_IMPLEMENTED</em> status.
     *
     * @return New BaseRegistrationService instance.
     */
    private BaseRegistrationService<ServiceConfigProperties> newRegistrationServiceWithoutImpls() {

        return new BaseRegistrationService<>() {

            @Override
            protected String getEventBusAddress() {
                return "requests.in";
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

            @Override
            protected Future<Void> updateDeviceLastVia(final String tenantId, final String deviceId,
                    final String gatewayId, final JsonObject deviceData) {
                return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
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
