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
package org.eclipse.hono.deviceregistry;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.service.http.HttpUtils.CONTENT_TYPE_JSON;
import static org.eclipse.hono.util.RegistrationConstants.REGISTRATION_ENDPOINT;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_DEVICE_ID;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.service.registration.RegistrationHttpEndpoint;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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

    private static final String HOST = "localhost";

    private static final String TENANT = "testTenant";
    private static final String DEVICE_ID = "testDeviceId";

    private static Vertx vertx;
    private static FileBasedRegistrationService registrationService;
    private static DeviceRegistryRestServer deviceRegistryRestServer;

    @BeforeClass
    public static void setUp(final TestContext context) {
        vertx = Vertx.vertx();

        Future<String> setupTracker = Future.future();
        setupTracker.setHandler(context.asyncAssertSuccess());

        ServiceConfigProperties restServerProps = new ServiceConfigProperties();
        restServerProps.setInsecurePortEnabled(true);
        restServerProps.setInsecurePort(0);

        RegistrationHttpEndpoint registrationHttpEndpoint = new RegistrationHttpEndpoint(vertx);
        deviceRegistryRestServer = new DeviceRegistryRestServer();
        deviceRegistryRestServer.addEndpoint(registrationHttpEndpoint);
        deviceRegistryRestServer.setConfig(restServerProps);

        FileBasedRegistrationConfigProperties regServiceProps = new FileBasedRegistrationConfigProperties();
        registrationService = new FileBasedRegistrationService();
        registrationService.setConfig(regServiceProps);
        SignatureSupportingConfigProperties signatureSupportingConfProps = new SignatureSupportingConfigProperties();
        signatureSupportingConfProps.setSharedSecret("DeviceRegistrySharedSecret_HasToBe32CharsOrLonger");
        registrationService.setRegistrationAssertionFactory(
                RegistrationAssertionHelperImpl.forSigning(vertx, signatureSupportingConfProps));

        Future<String> restServerDeploymentTracker = Future.future();
        vertx.deployVerticle(deviceRegistryRestServer, restServerDeploymentTracker.completer());
        restServerDeploymentTracker.compose(s -> {
            Future<String> registrationServiceDeploymentTracker = Future.future();
            vertx.deployVerticle(registrationService, registrationServiceDeploymentTracker.completer());
            return registrationServiceDeploymentTracker;
        }).compose(c -> setupTracker.complete(), setupTracker);
    }

    @AfterClass
    public static void tearDown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @After
    public void clearRegistry() throws InterruptedException {
        registrationService.clear();
    }

    private int getPort() {
        return deviceRegistryRestServer.getInsecurePort();
    }

    @Test
    public void testAddDevice(final TestContext context) {
        final String requestUri = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
        final String requestBody = new JsonObject().put(FIELD_DEVICE_ID, DEVICE_ID).put("test", "test").encode();
        final Async async = context.async();
        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    context.assertEquals(HTTP_CREATED, response.statusCode());
                    response.bodyHandler(totalBuffer -> {
                        context.assertTrue(totalBuffer.toString().isEmpty());
                        async.complete();
                    });
                }).exceptionHandler(context::fail).end(requestBody);
    }

    @Test
    public void testAddDeviceWithoutDeviceId(final TestContext context) {
        final String requestUri = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
        final String requestBody = new JsonObject().put("test", "test").encode();
        final Async async = context.async();
        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    context.assertEquals(HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
                }).exceptionHandler(context::fail).end(requestBody);
    }

    @Test
    public void testAddDisabledDevice(final TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        Future<Void> registerDeviceFuture = Future.future();
        // add the device with 'enabled' set to false
        final String requestUriForPOST = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
        final String requestBody = new JsonObject().put(FIELD_DEVICE_ID, DEVICE_ID).put("testKey", "testValue")
                .put("enabled", false).encode();
        vertx.createHttpClient().post(getPort(), HOST, requestUriForPOST).putHeader("content-type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    context.assertEquals(HTTP_CREATED, response.statusCode());
                    registerDeviceFuture.complete();
                }).exceptionHandler(registerDeviceFuture::fail).end(requestBody);
        registerDeviceFuture.compose(ar -> {
            // now read the device and verify "enabled" state
            final String requestUriForGET = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            vertx.createHttpClient().get(getPort(), HOST, requestUriForGET).handler(response -> {
                context.assertEquals(HTTP_OK, response.statusCode());
                response.bodyHandler(totalBuffer -> {
                    try {
                        JsonObject jsonObject = totalBuffer.toJsonObject();
                        context.assertEquals(DEVICE_ID, jsonObject.getString(FIELD_DEVICE_ID));
                        JsonObject dataInResponse = jsonObject.getJsonObject("data");
                        context.assertEquals(2, dataInResponse.size());
                        context.assertEquals("testValue", dataInResponse.getString("testKey"));
                        context.assertFalse(dataInResponse.getBoolean("enabled"));
                    } catch (Exception ex) {
                        done.fail(ex);
                    }
                    done.complete();
                });
            }).exceptionHandler(done::fail).end();
        }, done);
    }

    @Test
    public void testAddDeviceFailsAlreadyExists(final TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject data = new JsonObject();
        Future<Void> registerDeviceFuture = Future.future();
        // add the device
        registerDevice(DEVICE_ID, data, registerDeviceFuture);
        registerDeviceFuture.compose(ar -> {
            // now try to add the device again
            final String requestUri = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
            final String requestJson = new JsonObject().put(FIELD_DEVICE_ID, DEVICE_ID).put("test", "test").encode();
            vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                    .handler(response -> {
                        context.assertEquals(HTTP_CONFLICT, response.statusCode());
                        done.complete();
                    }).exceptionHandler(done::fail).end(requestJson);
        }, done);
    }

    @Test
    public void testAddDeviceFailsMissingContentType(final TestContext context) {
        final Async async = context.async();

        final String requestUri = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
        final String requestBody = FIELD_DEVICE_ID + "=" + DEVICE_ID + "&test=test";

        vertx.createHttpClient().post(getPort(), HOST, requestUri).handler(response -> {
            context.assertEquals(HTTP_BAD_REQUEST, response.statusCode());
            async.complete();
        }).exceptionHandler(context::fail).end(requestBody);
    }

    @Test
    public void testAddDeviceFailsMissingBody(final TestContext context) {
        final Async async = context.async();

        final String requestUri = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
        final String requestBody = "";

        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    context.assertEquals(HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
                }).exceptionHandler(context::fail).end(requestBody);
    }

    @Test
    public void testGetDevice(final TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject data = new JsonObject();
        data.put("key1", "value1");
        data.put("key2", "value2");
        Future<Void> registerDeviceFuture = Future.future();
        // add the device
        registerDevice(DEVICE_ID, data, registerDeviceFuture);
        registerDeviceFuture.compose(ar -> {
            // get the device
            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            vertx.createHttpClient().get(getPort(), HOST, requestUri).handler(response -> {
                context.assertEquals(HTTP_OK, response.statusCode());
                response.bodyHandler(totalBuffer -> {
                    try {
                        JsonObject jsonObject = totalBuffer.toJsonObject();
                        context.assertEquals(DEVICE_ID, jsonObject.getString(FIELD_DEVICE_ID));
                        JsonObject dataInResponse = jsonObject.getJsonObject("data");
                        context.assertEquals(3, dataInResponse.size());
                        context.assertEquals("value1", dataInResponse.getString("key1"));
                        context.assertEquals("value2", dataInResponse.getString("key2"));
                        context.assertTrue(dataInResponse.getBoolean("enabled"));
                    } catch (Exception ex) {
                        done.fail(ex);
                    }
                    done.complete();
                });
            }).exceptionHandler(done::fail).end();
        }, done);
    }

    private void registerDevice(final String deviceId, final JsonObject data, final Future<Void> resultFuture) {
        final String requestUri = String.format("/%s/%s", REGISTRATION_ENDPOINT, TENANT);
        final JsonObject requestJson = data.copy();
        requestJson.put(FIELD_DEVICE_ID, deviceId);

        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    if (response.statusCode() == HTTP_CREATED) {
                        resultFuture.complete();
                    } else {
                        resultFuture.fail("device registration failed; response status code: " + response.statusCode());
                    }
                }).exceptionHandler(resultFuture::fail).end(requestJson.encode());
    }

    @Test
    public void testUpdateDevice(final TestContext context) {
        testUpdateDeviceInternal(context, false);
    }

    @Test
    public void testUpdateDeviceWithDisablingDevice(final TestContext context) {
        testUpdateDeviceInternal(context, true);
    }

    private void testUpdateDeviceInternal(final TestContext context, final boolean disableDeviceOnUpdate) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject data = new JsonObject();
        data.put("key1", "value1");
        data.put("key2", "value2");
        Future<Void> registerDeviceFuture = Future.future();
        // add the device
        registerDevice(DEVICE_ID, data, registerDeviceFuture);
        registerDeviceFuture.compose(r -> {
            // update the device
            Future<Void> updateDeviceFuture = Future.future();

            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            final JsonObject requestJsonObject = new JsonObject().put(FIELD_DEVICE_ID, DEVICE_ID).put("newKey1", "newValue1");
            if (disableDeviceOnUpdate) {
                requestJsonObject.put("enabled", false);
            }
            vertx.createHttpClient().put(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                    .handler(response -> {
                        context.assertEquals(HTTP_NO_CONTENT, response.statusCode());
                        updateDeviceFuture.complete();
                    }).exceptionHandler(updateDeviceFuture::fail).end(requestJsonObject.encode());
            return updateDeviceFuture;
        }).compose(r -> {
            // get the device and verify returned data
            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            vertx.createHttpClient().get(getPort(), HOST, requestUri).handler(response -> {
                context.assertEquals(HTTP_OK, response.statusCode());
                response.bodyHandler(totalBuffer -> {
                    try {
                        JsonObject jsonObject = totalBuffer.toJsonObject();
                        context.assertEquals(DEVICE_ID, jsonObject.getString(FIELD_DEVICE_ID));
                        JsonObject dataInResponse = jsonObject.getJsonObject("data");
                        context.assertEquals(2, dataInResponse.size());
                        context.assertFalse(dataInResponse.containsKey("key1"));
                        context.assertEquals("newValue1", dataInResponse.getString("newKey1"));
                        context.assertEquals(!disableDeviceOnUpdate, dataInResponse.getBoolean("enabled"));
                        done.complete();
                    } catch (Exception ex) {
                        done.fail(ex);
                    }
                });
            }).exceptionHandler(done::fail).end();
        }, done);
    }

    @Test
    public void testUpdateDeviceFailsNoSuchDevice(final TestContext context) {
        final Async async = context.async();

        final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
        final String requestBody = new JsonObject().put(FIELD_DEVICE_ID, DEVICE_ID).put("test", "test").encode();

        vertx.createHttpClient().put(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    context.assertEquals(HTTP_NOT_FOUND, response.statusCode());
                    async.complete();
                }).exceptionHandler(context::fail).end(requestBody);
    }

    @Test
    public void testUpdateDeviceWithEmptyRequestBody(final TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject data = new JsonObject();
        data.put("enabled", false).put("testKey", "testValue");
        Future<Void> registerDeviceFuture = Future.future();
        // add the device
        registerDevice(DEVICE_ID, data, registerDeviceFuture);
        registerDeviceFuture.compose(ar -> {
            Future<Void> updateDeviceFuture = Future.future();

            // update the device with missing request body, causing the device data to be reset
            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            final String requestBody = "";
            vertx.createHttpClient().put(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                    .handler(response -> {
                        context.assertEquals(HTTP_NO_CONTENT, response.statusCode());
                        updateDeviceFuture.complete();
                    }).exceptionHandler(updateDeviceFuture::fail).end(requestBody);
            return updateDeviceFuture;
        }).compose(r -> {
            // get the device and verify returned data
            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            vertx.createHttpClient().get(getPort(), HOST, requestUri).handler(response -> {
                context.assertEquals(HTTP_OK, response.statusCode());
                response.bodyHandler(totalBuffer -> {
                    try {
                        JsonObject jsonObject = totalBuffer.toJsonObject();
                        context.assertEquals(DEVICE_ID, jsonObject.getString(FIELD_DEVICE_ID));
                        JsonObject dataInResponse = jsonObject.getJsonObject("data");
                        context.assertEquals(1, dataInResponse.size());
                        context.assertFalse(dataInResponse.containsKey("testKey"));
                        // deleting the data implies that the enabled flag was reset to its default value (true)
                        context.assertTrue(dataInResponse.getBoolean("enabled"));
                        done.complete();
                    } catch (Exception ex) {
                        done.fail(ex);
                    }
                });
            }).exceptionHandler(done::fail).end();
        }, done);
    }

    @Test
    public void testUpdateDeviceFailsMissingContentType(final TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject data = new JsonObject();
        Future<Void> registerDeviceFuture = Future.future();
        // add the device
        registerDevice(DEVICE_ID, data, registerDeviceFuture);
        registerDeviceFuture.compose(ar -> {
            // now try to update the device with missing content type
            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            final String requestBody = new JsonObject().put(FIELD_DEVICE_ID, DEVICE_ID).put("newKey1", "newValue1").encode();
            vertx.createHttpClient().put(getPort(), HOST, requestUri).handler(response -> {
                context.assertEquals(HTTP_BAD_REQUEST, response.statusCode());
                done.complete();
            }).exceptionHandler(done::fail).end(requestBody);
        }, done);
    }

    @Test
    public void testRemoveDevice(final TestContext context) {
        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject data = new JsonObject();
        Future<Void> registerDeviceFuture = Future.future();
        // add the device
        registerDevice(DEVICE_ID, data, registerDeviceFuture);
        registerDeviceFuture.compose(r -> {
            // remove the device
            Future<Void> updateDeviceFuture = Future.future();

            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            vertx.createHttpClient().delete(getPort(), HOST, requestUri).handler(response -> {
                context.assertEquals(HTTP_NO_CONTENT, response.statusCode());
                updateDeviceFuture.complete();
            }).exceptionHandler(updateDeviceFuture::fail).end();
            return updateDeviceFuture;
        }).compose(r -> {
            // try to get the device and verify HTTP_NOT_FOUND is returned
            final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);
            vertx.createHttpClient().get(getPort(), HOST, requestUri).handler(response -> {
                context.assertEquals(HTTP_NOT_FOUND, response.statusCode());
                done.complete();
            }).exceptionHandler(done::fail).end();
        }, done);
    }

    @Test
    public void testRemoveDeviceFailsNoSuchDevice(final TestContext context) {
        final Async async = context.async();

        final String requestUri = String.format("/%s/%s/%s", REGISTRATION_ENDPOINT, TENANT, DEVICE_ID);

        vertx.createHttpClient().delete(getPort(), HOST, requestUri).handler(response -> {
            context.assertEquals(HTTP_NOT_FOUND, response.statusCode());
            async.complete();
        }).exceptionHandler(context::fail).end();
    }
}
