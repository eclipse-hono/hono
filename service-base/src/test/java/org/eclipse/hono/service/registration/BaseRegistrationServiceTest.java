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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;

import java.net.HttpURLConnection;
import java.util.function.Function;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link AbstractRegistrationService}.
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
        final AbstractRegistrationService registrationService = newRegistrationService();

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
        final AbstractRegistrationService registrationService = newRegistrationService();

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
        final AbstractRegistrationService registrationService = newRegistrationService();

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
        final AbstractRegistrationService registrationService = newRegistrationService();

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
        final AbstractRegistrationService registrationService = newRegistrationService();

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
     * Verifies that a device's status cannot be asserted by a gateway that does not
     * match the device's configured gateway group.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForWrongGatewayGroup(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device and two gateways:
        // 1. the gateway that is member of the group the device is configured for.
        // 2. another gateway which is not member of that group
        final AbstractRegistrationService registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status for the wrong gateway group
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4715", "gw-2", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 403 status
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
            // and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }


    /**
     * Verifies that a device's status which use a gateway group cannot be asserted by a gateway that does not
     * have any group membership configured.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForGatewayWithNoGroup(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device and two gateways:
        // 1. the gateway that is member of the group the device is configured for.
        // 2. another gateway which is not member of that group
        final AbstractRegistrationService registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status for the wrong gateway group
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4715", "gw-3", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 403 status
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
            // and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a device's status which use a gateway group cannot be asserted by a gateway that does not
     * have any group membership configured.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsWithNoVia(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device and two gateways:
        // 1. the gateway that is member of the group the device is configured for.
        // 2. another gateway which is not member of that group
        final AbstractRegistrationService registrationService = newRegistrationService();

        // WHEN trying to assert the device's registration status for the wrong gateway group
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4716", "gw-4", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the assertion fails with a 403 status
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
            // and the response payload is empty
            assertNull(result.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the getDevice method returns "not implemented" if the base
     * {@link AbstractRegistrationService#getDevice(String, String, Span, Handler)} implementation is used.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testGetDeviceBaseImpl(final VertxTestContext ctx) {

        // GIVEN an empty registry (using the BaseRegistrationService#getDevice(String, String, Handler) implementation)
        final AbstractRegistrationService registrationService = newRegistrationServiceWithoutImpls();

        // WHEN trying to get a device's data
        registrationService.getDevice(Constants.DEFAULT_TENANT, "4711", NoopSpan.INSTANCE,
                ctx.succeeding(result -> ctx.verify(() -> {
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
        final AbstractRegistrationService registrationService = spy(newRegistrationService());

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", ctx.succeeding(result -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);

            // THEN the response contains the registered default content type
            final JsonObject defaults = payload.getJsonObject(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS);
            assertNotNull(defaults);
            assertEquals("application/default", defaults.getString(MessageHelper.SYS_PROPERTY_CONTENT_TYPE));

            ctx.completeNow();
        })));
    }

    /**
     * Verifies that a device's status can be asserted by any existing member of gateway group
     * where the device has been configured to connect via.
     *
     * @param ctx The vert.x unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationSucceedsForExistingGroup(final VertxTestContext ctx) {

        // GIVEN a registry that contains an enabled device that is configured to
        // connect via any of two enabled gateways.
        final AbstractRegistrationService registrationService = newRegistrationService();

        final Checkpoint assertion = ctx.checkpoint(2);



        // WHEN trying to assert the device's registration status for gateway 1
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4715", "gw-5", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);
            final JsonArray via = payload.getJsonArray(RegistrationConstants.FIELD_VIA);
            assertNotNull(via);
            assertEquals(via, new JsonArray().add("gw-5").add("gw-6"));
            assertion.flag();
        })));

        // WHEN trying to assert the device's registration status for gateway 4
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4715", "gw-6", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);
            final JsonArray via = payload.getJsonArray(RegistrationConstants.FIELD_VIA);
            assertNotNull(via);
            assertEquals(via, new JsonArray().add("gw-5").add("gw-6"));
            assertion.flag();
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
        final AbstractRegistrationService registrationService = newRegistrationService();

        final Checkpoint assertion = ctx.checkpoint(2);

        // WHEN trying to assert the device's registration status for gateway 1
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714", "gw-1", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);
            final JsonArray via = payload.getJsonArray(RegistrationConstants.FIELD_VIA);
            assertNotNull(via);
            assertEquals(via, new JsonArray().add("gw-1").add("gw-4"));
            assertion.flag();
        })));

        // WHEN trying to assert the device's registration status for gateway 4
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714", "gw-4", ctx.succeeding(result -> ctx.verify(() -> {
            // THEN the response contains a 200 status
            assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            final JsonObject payload = result.getPayload();
            assertNotNull(payload);
            final JsonArray via = payload.getJsonArray(RegistrationConstants.FIELD_VIA);
            assertNotNull(via);
            assertEquals(via, new JsonArray().add("gw-1").add("gw-4"));
            assertion.flag();
        })));
    }

    /**
     * Create a new BaseRegistrationService instance using the {@link #getDevice(String)} method to retrieve device data.
     *
     * @return New BaseRegistrationService instance.
     */
    private AbstractRegistrationService newRegistrationService() {
        return newRegistrationService(this::getDevice, this::resolveGatewayGroup);
    }

    private AbstractRegistrationService newRegistrationService(
            final Function<String, Future<RegistrationResult>> devices, final Function<JsonArray, Future<JsonArray>> resolveGatewayGroups) {

        return new AbstractRegistrationService() {

            @Override
            public void getDevice(final String tenantId, final String deviceId, final Span span,
                    final Handler<AsyncResult<RegistrationResult>> resultHandler) {
                devices.apply(deviceId).setHandler(resultHandler);
            }

            @Override
            protected void resolveGroupMembers(final String tenantId, final JsonArray via, final Span span, final Handler<AsyncResult<JsonArray>> resultHandler) {
                resolveGatewayGroups.apply(via).setHandler(resultHandler);
            }


        };
    }

    /**
     * Create a BaseRegistrationService where the <em>getDevice</em> and resolveGroupMembers methods are not implemented.
     *
     * @return New BaseRegistrationService instance.
     */
    private AbstractRegistrationService newRegistrationServiceWithoutImpls() {

        return new AbstractRegistrationService() {

            @Override
            protected void getDevice(final String tenantId, final String deviceId, final Span span,
                    final Handler<AsyncResult<RegistrationResult>> resultHandler) {
                resultHandler.handle(
                        Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
            }

            @Override
            protected void resolveGroupMembers(final String tenantId, final JsonArray via, final Span span, final Handler<AsyncResult<JsonArray>> resultHandler) {
                resultHandler.handle(
                        Future.succeededFuture(new JsonArray())
                );
            }
        };
    }

    /**
     * Wraps a given device ID and registration data into a JSON structure suitable to be returned to clients as the
     * result of a registration operation.
     *
     * @param deviceId The device ID.
     * @param data The registration data.
     * @return The JSON structure.
     */
    protected static final JsonObject getResultPayload(final String deviceId, final JsonObject data) {

        return new JsonObject()
                .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put(RegistrationConstants.FIELD_DATA, data);
    }

    private Future<RegistrationResult> getDevice(final String deviceId) {

        if ("4711".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "4711",
                    new JsonObject()
                        .put(RegistrationConstants.FIELD_ENABLED, true)
                        .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                        .put(RegistrationConstants.FIELD_VIA, "gw-1"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4712".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                        "4712",
                        new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false));
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4713".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "4713",
                    new JsonObject()
                        .put(RegistrationConstants.FIELD_ENABLED, true)
                        .put(RegistrationConstants.FIELD_VIA, "gw-3"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4714".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "4714",
                    new JsonObject()
                        .put(RegistrationConstants.FIELD_ENABLED, true)
                        .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                        .put(RegistrationConstants.FIELD_VIA, new JsonArray().add("gw-1").add("gw-4")));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4715".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "4715",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_PAYLOAD_DEFAULTS, new JsonObject()
                                    .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                            .put(RegistrationConstants.FIELD_VIA, "group-1"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4716".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "4716",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, true));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-1".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "gw-1",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, true));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-2".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "gw-2",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_MEMBER_OF, "group-2"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-3".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "gw-3",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-4".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "gw-4",
                    new JsonObject().put(RegistrationConstants.FIELD_ENABLED, true));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-5".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "gw-5",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_MEMBER_OF, "group-1"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("gw-6".equals(deviceId)) {
            final JsonObject responsePayload = getResultPayload(
                    "gw-6",
                    new JsonObject()
                            .put(RegistrationConstants.FIELD_ENABLED, true)
                            .put(RegistrationConstants.FIELD_MEMBER_OF, new JsonArray().add("group-1").add(("group-2"))));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else {
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
        }
    }

    private Future<JsonArray> resolveGatewayGroup(final JsonArray via) {
        if (new JsonArray().add("group-1").equals(via)) {
            return Future.succeededFuture(new JsonArray().add("gw-5").add("gw-6"));
        } else {
            return Future.succeededFuture(via);
        }




    }
}
