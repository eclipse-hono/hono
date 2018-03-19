/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import static org.mockito.Mockito.mock;

import java.net.HttpURLConnection;
import java.util.function.Function;

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

    /**
     * Time out each test case after 5 secs.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private static String secret = "dafhkjsdahfuksahuioahgfdahsgjkhfdjkg";
    private static SignatureSupportingConfigProperties props;
    private static Vertx vertx;

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
        Async startupFailure = ctx.async();
        Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertFailure(t -> startupFailure.complete()));
        registrationService.doStart(startFuture);

        // THEN startup fails
        startupFailure.await();
    }

    /**
     * Verifies that an enabled device's status can be asserted successfully.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationReturnsToken(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device with a default content type set
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", ctx.asyncAssertSuccess(result -> {
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_OK);
            JsonObject payload = result.getPayload();
            ctx.assertNotNull(payload);
            // THEN the response contains a JWT token asserting the device's registration status
            String compactJws = payload.getString(RegistrationConstants.FIELD_ASSERTION);
            ctx.assertNotNull(compactJws);
            // and contains the registered default content type
            JsonObject defaults = payload.getJsonObject(RegistrationConstants.FIELD_DEFAULTS);
            ctx.assertNotNull(defaults);
            ctx.assertEquals("application/default", defaults.getString(MessageHelper.SYS_PROPERTY_CONTENT_TYPE));
        }));
    }

    /**
     * Verifies that a disabled device's status cannot be asserted.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDisabledDevice(final TestContext ctx) {

        // GIVEN a registry that contains a disabled device
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
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
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert a device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "non-existent", ctx.asyncAssertSuccess(result -> {
            // THEN the response does not contain a JWT token
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_FOUND);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a device's status can be asserted by an existing gateway.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationSucceedsForExistingGateway(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device that is configured to
        // be connected to an enabled gateway
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status for a gateway
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", "gw-1", ctx.asyncAssertSuccess(result -> {
            // THEN the response contains a 200 status
            ctx.assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
            final JsonObject payload = result.getPayload();
            ctx.assertNotNull(payload);
            // and contains a JWT token
            ctx.assertNotNull(payload.getString(RegistrationConstants.FIELD_ASSERTION));
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
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
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
     * Verifies that a device's status cannot be asserted by a disabled gateway.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAssertDeviceRegistrationFailsForDisabledGateway(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device
        // and a gateway that the device is configured for but
        // which is disabled
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
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
        BaseRegistrationService<ServiceConfigProperties> registrationService = newRegistrationService();
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

        return new BaseRegistrationService<ServiceConfigProperties>() {

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
                        "4711",
                        new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false));
                return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_OK, responsePayload));
        } else if ("4713".equals(deviceId)) {
            final JsonObject responsePayload = BaseRegistrationService.getResultPayload(
                    "4713",
                    new JsonObject()
                        .put(RegistrationConstants.FIELD_ENABLED, true)
                        .put(BaseRegistrationService.PROPERTY_VIA, "gw-3"));
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
        } else {
            return Future.succeededFuture(RegistrationResult.from(HttpURLConnection.HTTP_NOT_FOUND));
        }
    }
}
