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

import static org.eclipse.hono.service.http.HttpEndpointUtils.CONTENT_TYPE_JSON;
import static org.eclipse.hono.util.CredentialsConstants.*;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_DEVICE_ID;

import io.vertx.core.json.JsonArray;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.credentials.CredentialsHttpEndpoint;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.*;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests the Credentials REST Interface of the {@link DeviceRegistryRestServer}.
 * Currently limited to the POST method only.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsRestServerTest {

    private static final String HOST = "localhost";

    private static final String TENANT = "testTenant";
    private static final String TEST_DEVICE_ID = "4711";
    private static final String TEST_AUTH_ID = "sensor20";

    private static Vertx vertx;
    private static FileBasedCredentialsService credentialsService;
    private static DeviceRegistryRestServer deviceRegistryRestServer;

    @BeforeClass
    public static void setUp(final TestContext context) {
        vertx = Vertx.vertx();

        final Future<String> setupTracker = Future.future();
        setupTracker.setHandler(context.asyncAssertSuccess());

        final ServiceConfigProperties restServerProps = new ServiceConfigProperties();
        restServerProps.setInsecurePortEnabled(true);
        restServerProps.setInsecurePort(0);

        final CredentialsHttpEndpoint credentialsHttpEndpoint = new CredentialsHttpEndpoint(vertx);
        deviceRegistryRestServer = new DeviceRegistryRestServer();
        deviceRegistryRestServer.addEndpoint(credentialsHttpEndpoint);
        deviceRegistryRestServer.setConfig(restServerProps);

        final FileBasedCredentialsConfigProperties credentialsServiceProps = new FileBasedCredentialsConfigProperties();
        credentialsService = new FileBasedCredentialsService();
        credentialsService.setConfig(credentialsServiceProps);

        final Future<String> restServerDeploymentTracker = Future.future();
        vertx.deployVerticle(deviceRegistryRestServer, restServerDeploymentTracker.completer());
        restServerDeploymentTracker.compose(s -> {
            Future<String> credentialsServiceDeploymentTracker = Future.future();
            vertx.deployVerticle(credentialsService, credentialsServiceDeploymentTracker.completer());
            return credentialsServiceDeploymentTracker;
        }).compose(c -> setupTracker.complete(), setupTracker);
    }

    @AfterClass
    public static void tearDown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @After
    public void clearRegistry() throws InterruptedException {
        credentialsService.clear();
    }

    private int getPort() {
        return deviceRegistryRestServer.getInsecurePort();
    }

    /**
     * Verify that a correctly filled json payload to add credentials is responded with {@link HttpURLConnection#HTTP_CREATED}
     * and an empty response message.
     */
    @Test
    public void testAddCredentials(final TestContext context)  {
        final String requestUri = buildCredentialsPostUri();

        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);

        final Async async = context.async();
        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("Content-Type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    context.assertEquals(HttpURLConnection.HTTP_CREATED, response.statusCode());
                    response.bodyHandler(totalBuffer -> {
                        context.assertTrue(totalBuffer.toString().isEmpty());
                        async.complete();
                    });
                }).exceptionHandler(context::fail).end(requestBodyAddCredentials.encodePrettily());
    }

    /**
     * Verify that a correctly filled json payload to add credentials for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     .
     */
    @Test
    public void testAddCredentialsConflictReported(final TestContext context)  {
        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);
        final Future<Integer> addCredentialsFuture = Future.future();
        addCredentials(requestBodyAddCredentials, addCredentialsFuture);

        final String requestUri = buildCredentialsPostUri();

        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        addCredentialsFuture.compose(ar -> {
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            // now try to add credentials again
            vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("content-type", CONTENT_TYPE_JSON)
                    .handler(response -> {
                        context.assertEquals(HttpURLConnection.HTTP_CONFLICT, response.statusCode());
                        done.complete();
                    }).exceptionHandler(done::fail).end(requestBodyAddCredentials.encodePrettily());
        }, done);
    }

    /**
     * Verify that a Content-Type for form-urlencoded data is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongContentType(final TestContext context)  {
        final String requestUri = buildCredentialsPostUri();
        final String contentType = "application/x-www-form-urlencoded";

        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, contentType, requestBodyAddCredentials, expectedStatus);
    }

    /**
     * Verify that an empty json payload to add credentials is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadEmpty(final TestContext context) {
        final String requestUri = buildCredentialsPostUri();

        final JsonObject requestBodyAddCredentials = new JsonObject();

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, CONTENT_TYPE_JSON, requestBodyAddCredentials, expectedStatus);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_DEVICE_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadPartsMissingDeviceId(final TestContext context) {
        testPostWithMissingPayloadParts(context, FIELD_DEVICE_ID);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_TYPE}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadPartsMissingType(final TestContext context) {
        testPostWithMissingPayloadParts(context, FIELD_TYPE);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_AUTH_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadPartsMissingAuthId(final TestContext context) {
        testPostWithMissingPayloadParts(context, FIELD_AUTH_ID);
    }

    /**
     * Verify that a correctly added credentials record can be successfully deleted again by using the device-id.
     */
    @Test
    public void testRenoveAddedCredentialsByDeviceId(final TestContext context) {
        final String requestUri = String.format("/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT, TEST_DEVICE_ID);

        addAndRemoveCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Verify that a correctly added credentials record can be successfully deleted again by using the type.
     */
    @Test
    public void testRenoveAddedCredentialsByType(final TestContext context)  {
        final String requestUri = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_DEVICE_ID, SECRETS_TYPE_HASHED_PASSWORD);

        addAndRemoveCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Verify that a correctly added credentials record can be successfully deleted again by using the type and authId.
     */
    @Test
    public void testRenoveAddedCredentialsByTypeAndAuthId(final TestContext context) {
        final String requestUri = String.format("/%s/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_DEVICE_ID, SECRETS_TYPE_HASHED_PASSWORD, TEST_AUTH_ID);

        addAndRemoveCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NO_CONTENT);
    }

    /**
     * Verify that a correctly added credentials record can not be deleted by using the correct authId but a not matching type.
     */
    @Test
    public void testRenoveAddedCredentialsByNotExistingTypeButWithAuthId(final TestContext context) {
        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);

        final Future<Integer> addCredentialsFuture = Future.future();
        addCredentials(requestBodyAddCredentials, addCredentialsFuture);

        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());
        final String deleteRequestUri = String.format("/%s/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_DEVICE_ID, "notExistingType", TEST_AUTH_ID);

        addCredentialsFuture.compose(ar -> {
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            // now try to remove credentials again
            vertx.createHttpClient().delete(getPort(), HOST, deleteRequestUri)
                    .handler(response -> {
                        context.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
                        done.complete();
                    }).exceptionHandler(done::fail).end();
        }, done);
    }

    /**
     * Verify that a non existing credentials record cannot be successfully deleted by using the device-id.
     */
    @Test
    public void testRenoveNonExistingCredentialsByDeviceId(final TestContext context) {
        final String requestUri = String.format("/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT, TEST_DEVICE_ID);
        final Async async = context.async();

        vertx.createHttpClient().delete(getPort(), HOST, requestUri)
                .handler(response -> {
                    context.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
                    async.complete();
                }).exceptionHandler(context::fail).end();
    }

    /**
     * Verify that a non existing credentials record cannot be successfully deleted by using an existing device-id but
     * a non-existing type.
     */
    @Test
    public void testRemoveNonExistingCredentialsByDeviceIdAndType(final TestContext context) {
        final String requestUri = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_DEVICE_ID, "invalid-type");

        addAndRemoveCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NOT_FOUND);
    }

    /**
     * Verify that a non existing credentials record cannot be successfully deleted by using an existing device-id and
     * an existing type but with an invalid auth-id.
     */
    @Test
    public void testRenoveNonExistingCredentialsByDeviceIdAndTypeAndAuthId(final TestContext context) {
        final String requestUri = String.format("/%s/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_DEVICE_ID, SECRETS_TYPE_HASHED_PASSWORD, "invalid-auth-id");

        addAndRemoveCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NOT_FOUND);
    }

    /**
     * Verify that a correctly added credentials record can be successfully looked up again by using the type and authId.
     */
    @Test
    public void testGetAddedCredentials(final TestContext context)  {
        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);
        final Future<Integer> addCredentialsFuture = Future.future();
        addCredentials(requestBodyAddCredentials, addCredentialsFuture);

        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final String requestUri = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_AUTH_ID, SECRETS_TYPE_HASHED_PASSWORD);

        addCredentialsFuture.compose(ar -> {
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            // now try to get credentials again
            vertx.createHttpClient().get(getPort(), HOST, requestUri)
                    .handler(response -> {
                        context.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                        response.bodyHandler(totalBuffer -> {
                            context.assertFalse(totalBuffer.toString().isEmpty()); // credentials object expected
                            // the answer must contain all of the payload of the add request, so test that now
                            context.assertTrue(testJsonObjectToBeContained(
                                    new JsonObject(totalBuffer.toString()), requestBodyAddCredentials));
                            done.complete();
                        });
                    }).exceptionHandler(done::fail).end();
        }, done);
    }

    @Test
    public void testGetAddedCredentialsMultipleTypes(final TestContext context)  {
        final JsonObject requestBodyAddCredentialsHashedPassword = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);
        final Future<Integer> addCredentialsFutureHashedPassword = Future.future();

        addCredentials(requestBodyAddCredentialsHashedPassword, addCredentialsFutureHashedPassword);

        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final String requestUriHashedPassword = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_AUTH_ID, SECRETS_TYPE_HASHED_PASSWORD);

        final JsonObject requestBodyAddCredentialsPresharedKey = buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID,
                TEST_AUTH_ID);
        final String requestUriPresharedKey = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                TEST_AUTH_ID, SECRETS_TYPE_PRESHARED_KEY);

        addCredentialsFutureHashedPassword.compose(ar -> {
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            final Future<Integer> addCredentialsFuturePresharedKey = Future.future();
            addCredentials(requestBodyAddCredentialsPresharedKey, addCredentialsFuturePresharedKey);
            return addCredentialsFuturePresharedKey;
        }).compose(ar -> {
            Future<Void> getDone = Future.future();
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            // now try to get credentials again
            vertx.createHttpClient().get(getPort(), HOST, requestUriHashedPassword)
                    .handler(response -> {
                        context.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                        response.bodyHandler(totalBuffer -> {
                            context.assertFalse(totalBuffer.toString().isEmpty()); // credentials object expected
                            // the answer must contain all of the payload of the add request, so test that now
                            context.assertTrue(testJsonObjectToBeContained(
                                    new JsonObject(totalBuffer.toString()), requestBodyAddCredentialsHashedPassword));
                            getDone.complete();
                        });
                    }).exceptionHandler(getDone::fail).end();
            return getDone;
        }).compose(ar -> {
            // now try to get the other credentials again
            vertx.createHttpClient().get(getPort(), HOST, requestUriPresharedKey)
                    .handler(response -> {
                        context.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                        response.bodyHandler(totalBuffer -> {
                            context.assertFalse(totalBuffer.toString().isEmpty()); // credentials object expected
                            // the answer must contain all of the payload of the add request, so test that now
                            context.assertTrue(testJsonObjectToBeContained(
                                    new JsonObject(totalBuffer.toString()), requestBodyAddCredentialsPresharedKey));
                            done.complete();
                        });
                    }).exceptionHandler(done::fail).end();
        }, done);
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong type.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongType(final TestContext context)  {
        final String requestUri = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                "notExistingType", TEST_AUTH_ID);
        addAndGetCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NOT_FOUND);
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong authId.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongAuthId(final TestContext context)  {
        final String requestUri = String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT,
                SECRETS_TYPE_HASHED_PASSWORD, "wrongAuthId");
        addAndGetCredentialsAgain(context, requestUri, HttpURLConnection.HTTP_NOT_FOUND);
    }

    private void addAndGetCredentialsAgain(final TestContext context, final String requestUri, final int expectedStatusCode) {
        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);
        final Future<Integer> addCredentialsFuture = Future.future();
        addCredentials(requestBodyAddCredentials, addCredentialsFuture);

        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        addCredentialsFuture.compose(ar -> {
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            // now try to get credentials again
            vertx.createHttpClient().get(getPort(), HOST, requestUri)
                    .handler(response -> {
                        context.assertEquals(expectedStatusCode, response.statusCode());
                        done.complete();
                    }).exceptionHandler(done::fail).end();
        }, done);
    }

    private void addAndRemoveCredentialsAgain(final TestContext context, final String requestUri, final int expectedStatusCode) {
        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);
        final Future<Integer> addCredentialsFuture = Future.future();
        addCredentials(requestBodyAddCredentials, addCredentialsFuture);

        final Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        addCredentialsFuture.compose(ar -> {
            context.assertTrue(ar == HttpURLConnection.HTTP_CREATED);
            // now try to remove credentials again
            vertx.createHttpClient().delete(getPort(), HOST, requestUri)
                    .handler(response -> {
                        context.assertEquals(expectedStatusCode, response.statusCode());
                        done.complete();
                    }).exceptionHandler(done::fail).end();
        }, done);
    }

    private String buildCredentialsPostUri() {
        return String.format("/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT);
    }

    private void postPayloadAndExpectErrorResponse(final TestContext context, final Async async, final String requestUri,
                                                   final String contentType, final JsonObject requestBody, final int expectedStatus) {
        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("Content-Type", contentType)
                .handler(response -> {
                    context.assertEquals(expectedStatus, response.statusCode());
                    response.bodyHandler(totalBuffer -> {
                        context.assertFalse(totalBuffer.toString().isEmpty()); // error message expected
                        async.complete();
                    });
                }).exceptionHandler(context::fail).end(requestBody.encodePrettily());
    }

    private JsonObject buildCredentialsPayloadHashedPassword(final String deviceId, final String authId) {
        final JsonObject secret = new JsonObject().
                put(FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(FIELD_SECRETS_HASH_FUNCTION, "sha-512").
                put(FIELD_SECRETS_SALT, "aG9ubw==").
                put(FIELD_SECRETS_PWD_HASH, "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA==");
        final JsonObject credPayload = new JsonObject().
                put(FIELD_DEVICE_ID, deviceId).
                put(FIELD_TYPE, SECRETS_TYPE_HASHED_PASSWORD).
                put(FIELD_AUTH_ID, authId).
                put(FIELD_SECRETS, new JsonArray().add(secret));
        return credPayload;
    }

    private JsonObject buildCredentialsPayloadPresharedKey(final String deviceId, final String authId) {
        final JsonObject secret = new JsonObject().
                put(FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(FIELD_SECRETS_KEY, "aG9uby1zZWNyZXQ="); // base64 "hono-secret"
        final JsonObject credPayload = new JsonObject().
                put(FIELD_DEVICE_ID, deviceId).
                put(FIELD_TYPE, SECRETS_TYPE_PRESHARED_KEY).
                put(FIELD_AUTH_ID, authId).
                put(FIELD_SECRETS, new JsonArray().add(secret));
        return credPayload;
    }

    /**
     * A simple implementation of subtree containment: all entries of the JsonObject that is tested to be contained
     * must be contained in the other JsonObject as well. Nested JsonObjects are treated the same by recursively callong
     * this method to test the containment.
     * Note that currently JsonArrays need to be equal and are not tested for containment (not necessary for our purposes
     * here).
     * @param jsonObject The JsonObject that must fully contain the other JsonObject (but may contain more entries as well).
     * @param jsonObjectToBeContained The JsonObject that needs to be fully contained inside the other JsonObject.
     * @return The result of the containment test.
     */
    private boolean testJsonObjectToBeContained(final JsonObject jsonObject, final JsonObject jsonObjectToBeContained) {
        if (jsonObjectToBeContained == null) {
            return true;
        }
        if (jsonObject == null) {
            return false;
        }
        AtomicBoolean containResult = new AtomicBoolean(true);

        jsonObjectToBeContained.forEach(entry -> {
            if (!jsonObject.containsKey(entry.getKey())) {
                containResult.set(false);
            } else {
                if (entry.getValue() == null) {
                    if (jsonObject.getValue(entry.getKey()) != null) {
                        containResult.set(false);
                    }
                } else if (entry.getValue() instanceof JsonObject) {
                    if (!(jsonObject.getValue(entry.getKey()) instanceof JsonObject)) {
                        containResult.set(false);
                    } else {
                        if (!testJsonObjectToBeContained((JsonObject)entry.getValue(),
                                (JsonObject)jsonObject.getValue(entry.getKey()))) {
                            containResult.set(false);
                        }
                    }
                } else {
                    if (!(entry.getValue().equals(jsonObject.getValue(entry.getKey())))) {
                        containResult.set(false);
                    }
                }
            }
        });
        return containResult.get();
    }

    private void addCredentials(final JsonObject requestPayload, final Future<Integer> resultFuture) {
        final String requestUri = buildCredentialsPostUri();

        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("Content-Type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                        resultFuture.complete(response.statusCode());
                    } else {
                        resultFuture.fail("add credentials failed; response status code: " + response.statusCode());
                    }
                }).exceptionHandler(resultFuture::fail).end(requestPayload.encodePrettily());
    }

    private void testPostWithMissingPayloadParts(final TestContext context, final String fieldMissing) {
        final String requestUri = buildCredentialsPostUri();

        final JsonObject requestBodyAddCredentials = buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, TEST_AUTH_ID);
        requestBodyAddCredentials.remove(fieldMissing);

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, CONTENT_TYPE_JSON, requestBodyAddCredentials, expectedStatus);
    }
}
