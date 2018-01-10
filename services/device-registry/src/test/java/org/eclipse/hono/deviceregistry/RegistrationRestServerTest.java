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
package org.eclipse.hono.deviceregistry;

import java.net.HttpURLConnection;
import java.net.InetAddress;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.registration.RegistrationHttpEndpoint;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests {@link DeviceRegistryRestServer} and {@link FileBasedRegistrationService} by making HTTP requests to the
 * device registration REST endpoints and validating the corresponding responses.
 */
@RunWith(VertxUnitRunner.class)
public class RegistrationRestServerTest {

    private static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();
    private static final long   TEST_TIMEOUT_SECONDS = 5;
    private static final String TENANT = "testTenant";
    private static final String DEVICE_ID = "testDeviceId";
    private static final String REGISTRATION_URI = String.format("/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, TENANT);

    private static Vertx vertx = Vertx.vertx();
    private static FileBasedRegistrationService registrationService;
    private static DeviceRegistryRestServer deviceRegistryRestServer;

    /**
     * Set the timeout for all test methods by using a JUnit Rule (instead of providing the timeout at every @Test annotation).
     * See {@link Test#timeout} for details about improved thread safety regarding the @After annotation for each test.
     */
    @Rule
    public final Timeout timeoutForAllMethods = Timeout.seconds(TEST_TIMEOUT_SECONDS);

    /**
     * Sets up fixture.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUp(final TestContext ctx) {

        final SignatureSupportingConfigProperties signatureSupportingConfProps = new SignatureSupportingConfigProperties();
        signatureSupportingConfProps.setSharedSecret("DeviceRegistrySharedSecret_HasToBe32CharsOrLonger");

        registrationService = new FileBasedRegistrationService();
        registrationService.setConfig(new FileBasedRegistrationConfigProperties());
        registrationService.setRegistrationAssertionFactory(
                RegistrationAssertionHelperImpl.forSigning(vertx, signatureSupportingConfProps));

        final Future<String> registrationServiceDeploymentTracker = Future.future();
        vertx.deployVerticle(registrationService, registrationServiceDeploymentTracker.completer());

        final ServiceConfigProperties restServerProps = new ServiceConfigProperties();
        restServerProps.setInsecurePort(0);

        deviceRegistryRestServer = new DeviceRegistryRestServer();
        deviceRegistryRestServer.setConfig(restServerProps);
        deviceRegistryRestServer.addEndpoint(new RegistrationHttpEndpoint(vertx));

        final Future<String> restServerDeploymentTracker = Future.future();
        vertx.deployVerticle(deviceRegistryRestServer, restServerDeploymentTracker.completer());

        CompositeFuture.all(restServerDeploymentTracker, registrationServiceDeploymentTracker).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Removes all entries from the registration service.
     */
    @After
    public void clearRegistry() {
        registrationService.clear();
    }

    /**
     * Shuts down the server.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void tearDown(final TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    private int getPort() {
        return deviceRegistryRestServer.getInsecurePort();
    }

    /**
     * Verifies that a device can be properly registered.
     * 
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceSucceeds(final TestContext ctx) {

        final String requestBody = new JsonObject()
                .put(RequestResponseApiConstants.FIELD_DEVICE_ID, DEVICE_ID)
                .put("test", "test")
                .encode();
        final Async async = ctx.async();

        vertx.createHttpClient()
            .post(getPort(), HOST, REGISTRATION_URI)
            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
            .handler(response -> {
                ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.statusCode());
                response.bodyHandler(totalBuffer -> {
                    ctx.assertTrue(totalBuffer.length() == 0);
                    async.complete();
                });
            }).exceptionHandler(ctx::fail)
            .end(requestBody);
    }

    /**
     * Verifies that a device cannot be registered if the request body
     * does not contain a device identifier.
     * 
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsWithoutDeviceId(final TestContext ctx) {

        final String requestBody = new JsonObject().put("test", "test").encode();
        final Async async = ctx.async();

        vertx.createHttpClient()
            .post(getPort(), HOST, REGISTRATION_URI)
            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
            .handler(response -> {
                ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                async.complete();
            }).exceptionHandler(ctx::fail)
            .end(requestBody);
    }

    /**
     * Verifies that a device can be registered only once.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAddDeviceFailsForDuplicateDevice(final TestContext ctx) {

        final JsonObject data = new JsonObject();
        // add the device
        registerDevice(DEVICE_ID, data).setHandler(ctx.asyncAssertSuccess(s -> {
            // now try to add the device again
            registerDevice(DEVICE_ID, data).setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, ((ServiceInvocationException) t).getErrorCode());
            }));
        }));
    }

    /**
     * Verifies that a device cannot be registered if the request
     * does not contain a content type.
     * 
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForMissingContentType(final TestContext ctx) {

        final Async async = ctx.async();
        final String requestBody = new JsonObject()
                .put(RegistrationConstants.FIELD_DEVICE_ID, DEVICE_ID)
                .put("test", "test")
                .encode();

        vertx.createHttpClient()
            .post(getPort(), HOST, REGISTRATION_URI)
            .handler(response -> {
                ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                async.complete();
            }).exceptionHandler(ctx::fail)
            .end(requestBody);
    }

    /**
     * Verifies that a device cannot be registered if the request
     * does not contain a body.
     * 
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddDeviceFailsForMissingBody(final TestContext ctx) {

        final Async async = ctx.async();

        vertx.createHttpClient()
            .post(getPort(), HOST, REGISTRATION_URI)
            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
            .handler(response -> {
                ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                async.complete();
            }).exceptionHandler(ctx::fail)
            .end();
    }

    /**
     * Verifies that the information that has been registered for a device
     * is contained in the result when retrieving registration informaiton
     * for the device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceContainsRegisteredInfo(final TestContext ctx) {

        final JsonObject data = new JsonObject()
                .put("testString", "testValue")
                .put("testBoolean", Boolean.FALSE)
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);

        registerDevice(DEVICE_ID, data)
            .compose(ok -> getRegistrationInfo(DEVICE_ID))
            .compose(info -> {
                assertRegistrationInformation(ctx, info, DEVICE_ID, data);
                return Future.succeededFuture();
            }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a request for registration information fails for
     * a device that is not registered.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetDeviceFailsForNonExistingDevice(final TestContext ctx) {

        getRegistrationInfo("non-existing-device").setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the registration information provided when updating
     * a device replaces the existing information.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceSucceeds(final TestContext ctx) {

        final JsonObject originalData = new JsonObject()
                .put("key1", "value1")
                .put("key2", "value2")
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject updatedData = new JsonObject()
                .put("newKey1", "newValue1")
                .put(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);

        registerDevice(DEVICE_ID, originalData)
            .compose(ok -> updateDevice(DEVICE_ID, updatedData))
            .compose(ok -> getRegistrationInfo(DEVICE_ID))
            .compose(info -> {
                assertRegistrationInformation(ctx, info, DEVICE_ID, updatedData);
                return Future.succeededFuture();
            }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that an update request fails if the device does not exist.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForNonExistingDevice(final TestContext ctx) {

        updateDevice("non-existing-device", new JsonObject().put("test", "test")).setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that an update request fails if it contains no content type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateDeviceFailsForMissingContentType(final TestContext context) {

        registerDevice(DEVICE_ID, new JsonObject()).compose(ok -> {
            // now try to update the device with missing content type
            final String requestUri = String.format("/%s/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            final String requestBody = new JsonObject().put(RequestResponseApiConstants.FIELD_DEVICE_ID, DEVICE_ID).put("newKey1", "newValue1").encode();
            final Future<Void> result = Future.future();
            vertx.createHttpClient()
                .put(getPort(), HOST, requestUri)
                .handler(response -> {
                    context.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                    result.complete();
                }).exceptionHandler(result::fail)
                .end(requestBody);
            return result;
            }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that no registration info can be retrieved anymore
     * once a device has been deregistered.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceSucceeds(final TestContext ctx) {

        registerDevice(DEVICE_ID, new JsonObject())
            .compose(ok -> deregisterDevice(DEVICE_ID))
            .compose(ok -> {
                return getRegistrationInfo(DEVICE_ID)
                        .compose(info -> Future.failedFuture("get registration info should have failed"))
                        .recover(t -> {
                            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
                            return Future.succeededFuture();
                        });
            }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a request to deregister a non-existing device fails.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterDeviceFailsForNonExisingDevice(final TestContext ctx) {

        deregisterDevice("non-existing-device").setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private Future<Void> registerDevice(final String deviceId, final JsonObject data) {

        final JsonObject requestJson = data.copy();
        requestJson.put(RegistrationConstants.FIELD_DEVICE_ID, deviceId);
        final Future<Void> result = Future.future();

        vertx.createHttpClient()
            .post(getPort(), HOST, REGISTRATION_URI)
            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
            .handler(response -> {
                if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                    result.complete();
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode(), "device registration failed: " + response.statusCode()));
                }
            }).exceptionHandler(result::fail)
            .end(requestJson.encode());

        return result;
    }

    private Future<Void> updateDevice(final String deviceId, final JsonObject data) {

        final String requestUri = String.format("/%s/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, TENANT, deviceId);
        final JsonObject requestJson = data.copy();
        requestJson.put(RegistrationConstants.FIELD_DEVICE_ID, deviceId);
        final Future<Void> result = Future.future();

        vertx.createHttpClient()
            .put(getPort(), HOST, requestUri)
            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
            .handler(response -> {
                if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                    result.complete();
                } else {
                    result.fail(new ServiceInvocationException(response.statusCode(), "device update failed: " + response.statusCode()));
                }
            }).exceptionHandler(result::fail)
            .end(requestJson.encode());

        return result;
    }

    private Future<JsonObject> getRegistrationInfo(final String deviceId) {

        final Future<JsonObject> result = Future.future();
        final String requestUri = String.format("/%s/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, TENANT, deviceId);
        vertx.createHttpClient()
            .get(getPort(), HOST, requestUri)
            .handler(response -> {
                if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                    result.fail(new ClientErrorException(response.statusCode()));
                } else {
                    response.bodyHandler(totalBuffer -> {
                        try {
                            result.complete(totalBuffer.toJsonObject());
                        } catch (DecodeException ex) {
                            result.fail(ex);
                        }
                    });
                }
            }).exceptionHandler(result::fail)
            .end();

        return result;
    }

    private Future<JsonObject> deregisterDevice(final String deviceId) {

        final Future<JsonObject> result = Future.future();
        final String requestUri = String.format("/%s/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, TENANT, deviceId);
        vertx.createHttpClient()
            .delete(getPort(), HOST, requestUri)
            .handler(response -> {
                if (response.statusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                    result.fail(new ClientErrorException(response.statusCode()));
                } else {
                    result.complete();
                }
            }).exceptionHandler(result::fail)
            .end();

        return result;
    }

    private static void assertRegistrationInformation(
            final TestContext ctx,
            final JsonObject response,
            final String expectedDeviceId,
            final JsonObject expectedData) {

        ctx.assertEquals(expectedDeviceId, response.getString(RegistrationConstants.FIELD_DEVICE_ID));
        JsonObject registeredData = response.getJsonObject(RegistrationConstants.FIELD_DATA);
        registeredData.forEach(entry -> {
            ctx.assertEquals(expectedData.getValue(entry.getKey()), entry.getValue());
        });
    }


}
