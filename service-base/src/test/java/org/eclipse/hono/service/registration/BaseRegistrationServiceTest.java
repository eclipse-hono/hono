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

import static org.mockito.Mockito.mock;

import java.net.HttpURLConnection;
import java.util.function.Function;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link BaseRegistrationService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class BaseRegistrationServiceTest {

    private static String secret = "dafhkjsdahfuksahuioahgfdahsgjkhfdjkg";
    private static SignatureSupportingConfigProperties props;
    private static Vertx vertx;

    /**
     * Time out each test case after 5 secs.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    /**
     * Initializes common properties.
     */
    @BeforeClass
    public static void init() {
        vertx = mock(Vertx.class);
        props = new SignatureSupportingConfigProperties();
        props.setSharedSecret(secret);
    }

    /**
     * Verifies that the service cannot be started without either <em>signingSecret</em> or
     * <em>signingKeyPath</em> being set.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testStartupFailsIfNoRegistrationAssertionFactoryIsSet(final TestContext ctx) {

        // GIVEN a registry without an assertion factory being set
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();

        // WHEN starting the service
        final Async startupFailure = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertFailure(t -> startupFailure.complete()));
        registrationService.doStart(startFuture);

        // THEN startup fails
        startupFailure.await();
    }

    /**
     * Verifies that a disabled device's status cannot be asserted.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDisabledDevice(final TestContext ctx) {

        // GIVEN a registry that contains a disabled device
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4712", ctx.asyncAssertSuccess(result -> {
            // THEN the response does not contain a JWT token
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_FOUND);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a non existing device's status cannot be asserted.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForNonExistingDevice(final TestContext ctx) {

        // GIVEN a registry that does not contain any devices
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert a device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "non-existent", ctx.asyncAssertSuccess(result -> {
            // THEN the response does not contain a JWT token
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_FOUND);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a device's status cannot be asserted by a non-existing gateway.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForNonExistingGateway(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device but no gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status for a gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", "non-existent-gw", ctx.asyncAssertSuccess(result -> {
            // THEN the response contains a 403 status
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_FORBIDDEN);
            // and does not contain a JWT token
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a device's status cannot be asserted if there are multiple <em>via</em> entries
     * defined for the device and the update of the <em>last-via</em> property cannot be performed
     * because an update of device registration data is not supported. 
     *
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDeviceWithMultipleVias(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device that is configured to
        // be connected to an enabled gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status for the gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4714", "gw-1", ctx.asyncAssertSuccess(result -> {
            // THEN the response contains a 501 status
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_IMPLEMENTED);
            // and does not contain a JWT token
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a device's status cannot be asserted by a disabled gateway.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDisabledGateway(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device
        // and a gateway that the device is configured for but
        // which is disabled
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status for a gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4713", "gw-3", ctx.asyncAssertSuccess(result -> {
            // THEN the response contains a 403 status
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_FORBIDDEN);
            // and does not contain a JWT token
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a device's status cannot be asserted by a gateway that does not
     * match the device's configured gateway.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForWrongGateway(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device and two gateways:
        // 1. the gateway that the device is configured for.
        // 2. another gateway
        final BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status for the wrong gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", "gw-2", ctx.asyncAssertSuccess(result -> {
            // THEN the response contains a 403 status
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_FORBIDDEN);
            // and does not contain a JWT token
            ctx.assertNull(result.getPayload());
        }));
    }

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
                        .put(RegistrationConstants.FIELD_DEFAULTS, new JsonObject()
                                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                        .put(BaseRegistrationService.PROPERTY_VIA, "gw-1"));
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
                        .put(BaseRegistrationService.PROPERTY_VIA, "gw-3"));
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4714".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "4714",
                    new JsonObject()
                        .put(RegistrationConstants.FIELD_ENABLED, true)
                        .put(RegistrationConstants.FIELD_DEFAULTS, new JsonObject()
                                .put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, "application/default"))
                        .put(BaseRegistrationService.PROPERTY_VIA, new JsonArray().add("gw-1").add("gw-4")));
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
