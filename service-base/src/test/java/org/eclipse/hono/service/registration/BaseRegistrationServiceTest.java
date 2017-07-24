/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.mockito.Mockito.mock;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.Before;
import org.junit.Test;
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
 * BaseRegistrationServiceTest
 *
 */
@RunWith(VertxUnitRunner.class)
public class BaseRegistrationServiceTest {

    private static String secret = "dafhkjsdahfuksahuioahgfdahsgjkhfdjkg";
    private Vertx vertx;
    private SignatureSupportingConfigProperties props;

    /**
     * Initializes common properties.
     */
    @Before
    public void init() {
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
        BaseRegistrationService<ServiceConfigProperties> registrationService = getRegistrationService(HTTP_OK,
                BaseRegistrationService.getResultPayload("4711", new JsonObject()));

        // WHEN starting the service
        Async startupFailure = ctx.async();
        Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertFailure(t -> startupFailure.complete()));
        registrationService.start(startFuture);

        // THEN startup fails
        startupFailure.await(1000);
    }

    /**
     * Verifies that an enabled device's status can be asserted successfully.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test(timeout = 2000)
    public void testAssertDeviceRegistrationReturnsToken(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device
        BaseRegistrationService<ServiceConfigProperties> registrationService = getRegistrationService(HTTP_OK,
                BaseRegistrationService.getResultPayload("4711", new JsonObject()));
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", ctx.asyncAssertSuccess(result -> {
            // THEN the response contains a JWT token asserting the device's registration status
            ctx.assertEquals(result.getStatus(), HTTP_OK);
            JsonObject payload = result.getPayload();
            ctx.assertNotNull(payload);
            String compactJws = payload.getString(RegistrationConstants.FIELD_ASSERTION);
            ctx.assertNotNull(compactJws);
        }));
    }

    /**
     * Verifies that a disabled device's status cannot be asserted.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test(timeout = 2000)
    public void testAssertDeviceRegistrationFailsForDisabledDevice(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device
        BaseRegistrationService<ServiceConfigProperties> registrationService = getRegistrationService(HTTP_OK,
                BaseRegistrationService.getResultPayload("4711", new JsonObject().put(RegistrationConstants.FIELD_ENABLED, false)));
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", ctx.asyncAssertSuccess(result -> {
            // THEN the response does not contain a JWT token
            ctx.assertEquals(result.getStatus(), HTTP_NOT_FOUND);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that a non existing device's status cannot be asserted.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test(timeout = 2000)
    public void testAssertDeviceRegistrationFailsForNonExistingDevice(final TestContext ctx) {

        // GIVEN a registry that contains an enabled device
        BaseRegistrationService<ServiceConfigProperties> registrationService = getRegistrationService(HTTP_NOT_FOUND, null);
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to assert the device's registration status
        registrationService.assertRegistration(Constants.DEFAULT_TENANT, "4711", ctx.asyncAssertSuccess(result -> {
            // THEN the response does not contain a JWT token
            ctx.assertEquals(result.getStatus(), HTTP_NOT_FOUND);
            ctx.assertNull(result.getPayload());
        }));
    }

    private BaseRegistrationService<ServiceConfigProperties> getRegistrationService(final int status, final JsonObject data) {

        return new BaseRegistrationService<ServiceConfigProperties>() {

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }

            @Override
            public void updateDevice(final String tenantId, final String deviceId, final JsonObject otherKeys, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
            }

            @Override
            public void removeDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
            }

            @Override
            public void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(RegistrationResult.from(status, data)));
            }

            @Override
            public void findDevice(final String tenantId, final String key, final String value, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
            }

            @Override
            public void addDevice(final String tenantId, final String deviceId, JsonObject otherKeys, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
            }
        };
    }
}
