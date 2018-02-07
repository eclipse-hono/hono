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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.credentials.CredentialsHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests the Credentials REST Interface of the {@link DeviceRegistryRestServer}.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsRestServerTest {

    private static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();
    private static final String TENANT = "testTenant";
    private static final String URI_ADD_CREDENTIALS = "/" + CredentialsConstants.CREDENTIALS_ENDPOINT + "/" + TENANT;
    private static final String TEMPLATE_URI_CREDENTIALS_INSTANCE = String.format("/%s/%s/%%s/%%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT);
    private static final String TEMPLATE_URI_CREDENTIALS_BY_DEVICE = String.format("/%s/%s/%%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT);
    private static final String TEST_DEVICE_ID = "4711";
    private static final String TEST_AUTH_ID = "sensor20";
    private static final long TEST_TIMEOUT_MILLIS = 2000;
    private static final Vertx vertx = Vertx.vertx();

    private static FileBasedCredentialsService credentialsService;
    private static DeviceRegistryRestServer deviceRegistryRestServer;

    /**
     * Set the timeout for all test methods by using a JUnit Rule (instead of providing the timeout at every @Test annotation).
     * See {@link Test#timeout} for details about improved thread safety regarding the @After annotation for each test.
     */
    @Rule
    public final TestRule timeoutForAllMethods = Timeout.millis(TEST_TIMEOUT_MILLIS);

    /**
     * Deploys the server to vert.x.
     * 
     * @param context The vert.x test context.
     */
    @BeforeClass
    public static void setUp(final TestContext context) {

        final Future<String> restServerDeploymentTracker = Future.future();
        final Future<String> credentialsServiceDeploymentTracker = Future.future();

        credentialsService = new FileBasedCredentialsService();
        credentialsService.setConfig(new FileBasedCredentialsConfigProperties());
        vertx.deployVerticle(credentialsService, credentialsServiceDeploymentTracker.completer());

        final ServiceConfigProperties restServerProps = new ServiceConfigProperties();
        restServerProps.setInsecurePortEnabled(true);
        restServerProps.setInsecurePort(0);
        restServerProps.setInsecurePortBindAddress(HOST);

        deviceRegistryRestServer = new DeviceRegistryRestServer();
        deviceRegistryRestServer.addEndpoint(new CredentialsHttpEndpoint(vertx));
        deviceRegistryRestServer.setConfig(restServerProps);
        vertx.deployVerticle(deviceRegistryRestServer, restServerDeploymentTracker.completer());

        CompositeFuture.all(restServerDeploymentTracker, credentialsServiceDeploymentTracker)
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Shuts down the server.
     * 
     * @param context The vert.x test context.
     */
    @AfterClass
    public static void tearDown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    /**
     * Removes all entries from the Credentials service.
     */
    @After
    public void clearRegistry() {
        credentialsService.clear();
    }

    private static int getPort() {
        return deviceRegistryRestServer.getInsecurePort();
    }

    /**
     * Verifies that the service accepts an add credentials request containing valid credentials
     * and that the response contains a <em>Location</em> header for the created resource.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceeds(final TestContext context)  {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject requestBodyAddCredentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);
        addCredentials(requestBodyAddCredentials).setHandler(context.asyncAssertSuccess(r -> {
            context.assertNotNull(r.getHeader(HttpHeaders.LOCATION));
        }));
    }

    /**
     * Verify that a correctly filled json payload to add credentials for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsRejectsDuplicateRegistration(final TestContext context)  {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject requestBodyAddCredentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);

        addCredentials(requestBodyAddCredentials).compose(ar -> {
            // now try to add credentials again
            return addCredentials(requestBodyAddCredentials, HttpURLConnection.HTTP_CONFLICT);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with a Content-Type
     * other than application/json.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForWrongContentType(final TestContext context)  {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        addCredentials(
                DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId),
                "application/x-www-form-urlencoded",
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with an empty body.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForEmptyBody(final TestContext context) {

        addCredentials(null, HttpUtils.CONTENT_TYPE_JSON, HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_DEVICE_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingDeviceId(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_DEVICE_ID);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_TYPE}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingType(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_TYPE);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_AUTH_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingAuthId(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_AUTH_ID);
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsSucceeds(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject orig = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);
        final JsonObject altered = orig.copy();
        altered.put(CredentialsConstants.FIELD_DEVICE_ID, "other-device");

        addCredentials(orig).compose(ar -> {
            return updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, altered);
        }).compose(ur -> {
            context.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, ur.statusCode());
            return getCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, b -> {
                context.assertEquals("other-device", b.toJsonObject().getString(CredentialsConstants.FIELD_DEVICE_ID));
            });
        }).setHandler(context.asyncAssertSuccess(gr -> {
            context.assertEquals(HttpURLConnection.HTTP_OK, gr.statusCode());
        }));
    }

    /**
     * Verifies that the service rejects an update request for non-existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonExistingCredentials(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject altered = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);

        updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, altered, HttpURLConnection.HTTP_NOT_FOUND)
            .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service rejects an update request for credentials containing a different type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonMatchingTypeInPayload(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject orig = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);
        final JsonObject altered = orig.copy().put(CredentialsConstants.FIELD_TYPE, "non-matching-type");

        addCredentials(orig).compose(ar -> {
            return updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, altered, HttpURLConnection.HTTP_BAD_REQUEST);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service rejects an update request for credentials containing a different authentication
     * identifier.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonMatchingAuthIdInPayload(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject orig = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);
        final JsonObject altered = orig.copy().put(CredentialsConstants.FIELD_AUTH_ID, "non-matching-auth-id");

        addCredentials(orig).compose(ar -> {
            return updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, altered, HttpURLConnection.HTTP_BAD_REQUEST);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can be successfully deleted again by using the device-id.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsForDeviceSucceeds(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject credentials = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);
        addCredentials(credentials).compose(ar -> {
            return removeCredentials(TEST_DEVICE_ID, HttpURLConnection.HTTP_NO_CONTENT);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that a correctly added credentials record can be successfully deleted again by using the type and authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsSucceeds(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject credentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);
        addCredentials(credentials).compose(ar -> {
            // now try to remove credentials again
            return removeCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        }).setHandler(context.asyncAssertSuccess(rr -> {
            context.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, rr.statusCode());
        }));
    }

    /**
     * Verify that a correctly added credentials record can not be deleted by using the correct authId but a non matching type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsFailsForWrongType(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject requestBodyAddCredentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);

        addCredentials(requestBodyAddCredentials).compose(ar -> {
            // now try to remove credentials again
            return removeCredentials(authId, "wrong-type");
        }).setHandler(context.asyncAssertSuccess(rr -> {
            context.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, rr.statusCode());
        }));
    }

    /**
     * Verifies that a request to delete all credentials for a device fails if no credentials exist
     * for the device.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsForDeviceFailsForNonExistingCredentials(final TestContext context) {

        removeCredentials("non-existing-device", HttpURLConnection.HTTP_NOT_FOUND).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can be successfully looked up again by using the type and authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentials(final TestContext context)  {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject requestBody = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);

        addCredentials(requestBody).compose(ar -> {
            // now try to get credentials again
            return getCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, b -> {
                context.assertTrue(DeviceRegistryTestUtils.testJsonObjectToBeContained(b.toJsonObject(), requestBody));
            });
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that multiple (2) correctly added credentials records of the same authId can be successfully looked up by single
     * requests using their type and authId again.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsMultipleTypesSingleRequests(final TestContext context) {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject hashedPasswordCredentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);
        final JsonObject presharedKeyCredentials = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);

        final List<JsonObject> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(hashedPasswordCredentials);
        credentialsListToAdd.add(presharedKeyCredentials);

        addMultipleCredentials(credentialsListToAdd).compose(ar -> {
            return getCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, b -> {
                context.assertTrue(DeviceRegistryTestUtils.testJsonObjectToBeContained(b.toJsonObject(), hashedPasswordCredentials));
            });
        }).compose(gr -> {
            return getCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, b -> {
                context.assertTrue(DeviceRegistryTestUtils.testJsonObjectToBeContained(b.toJsonObject(), presharedKeyCredentials));
            });
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of authentication identifier.
     * <p>
     * The returned JsonObject must consist of the total number of entries and contain all previously added credentials
     * in the provided JsonArray that is found under the key of the endpoint {@link CredentialsConstants#CREDENTIALS_ENDPOINT}.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    public void testGetAllCredentialsForDeviceSucceeds(final TestContext context) throws InterruptedException {

        final List<JsonObject> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, "auth"));
        credentialsListToAdd.add(DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, "other-auth"));

        addMultipleCredentials(credentialsListToAdd).compose(ar -> {
            // now try to get all these credentials again
            return getCredentials(TEST_DEVICE_ID, b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonObject(), credentialsListToAdd);
            });
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of type.
     * <p>
     * The returned JsonObject must consist of the total number of entries and contain all previously added credentials
     * in the provided JsonArray that is found under the key of the endpoint {@link CredentialsConstants#CREDENTIALS_ENDPOINT}.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    public void testGetCredentialsForDeviceRegardlessOfType(final TestContext context) throws InterruptedException {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final List<JsonObject> credentialsToAdd = new ArrayList<>();
        for(int i = 0; i < 5; i++) {
            final JsonObject requestBody = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, authId);
            requestBody.put(CredentialsConstants.FIELD_TYPE, "type" + i);
            credentialsToAdd.add(requestBody);
        }
        addMultipleCredentials(credentialsToAdd).compose(ar -> {
            // now try to get all these credentials again
            return getCredentials(TEST_DEVICE_ID, b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonObject(), credentialsToAdd);
            });
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongType(final TestContext context)  {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject credentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);
        addCredentials(credentials).compose(ar -> {
            return getCredentials(authId, "wrong-type", HttpURLConnection.HTTP_NOT_FOUND, null);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongAuthId(final TestContext context)  {

        final String authId = getRandomAuthId(TEST_AUTH_ID);
        final JsonObject credentials = DeviceRegistryTestUtils.buildCredentialsPayloadHashedPassword(TEST_DEVICE_ID, authId);
        addCredentials(credentials).compose(ar -> {
            return getCredentials("wrong-auth-id", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, HttpURLConnection.HTTP_NOT_FOUND, null);
        }).setHandler(context.asyncAssertSuccess());
    }

    private static Future<Integer> addMultipleCredentials(final List<JsonObject> credentialsList) {

        final Future<Integer> result = Future.future();
        @SuppressWarnings("rawtypes")
        final List<Future> addTrackers = new ArrayList<>();
        for (JsonObject creds : credentialsList) {
            addTrackers.add(addCredentials(creds));
        }

        CompositeFuture.all(addTrackers).setHandler(r -> {
            if (r.succeeded()) {
                result.complete(HttpURLConnection.HTTP_CREATED);
            } else {
                result.fail(r.cause());
            }
        });
        return result;
    }

    private static void assertResponseBodyContainsAllCredentials(final TestContext context, final JsonObject responseBody,
            final List<JsonObject> credentialsList) {

        // the response must contain all of the payload of the add request, so test that now
        context.assertTrue(responseBody.containsKey(CredentialsConstants.FIELD_CREDENTIALS_TOTAL));
        Integer totalCredentialsFound = responseBody.getInteger(CredentialsConstants.FIELD_CREDENTIALS_TOTAL);
        context.assertEquals(totalCredentialsFound, credentialsList.size());
        context.assertTrue(responseBody.containsKey(CredentialsConstants.CREDENTIALS_ENDPOINT));
        final JsonArray credentials = responseBody.getJsonArray(CredentialsConstants.CREDENTIALS_ENDPOINT);
        context.assertNotNull(credentials);
        context.assertEquals(credentials.size(), totalCredentialsFound);
        // TODO: add full test if the lists are 'identical' (contain the same JsonObjects by using the
        //       contained helper method)
    }

    private static String getRandomAuthId(final String authIdPrefix) {
        return authIdPrefix + "." + UUID.randomUUID();
    }

    private static void testAddCredentialsWithMissingPayloadParts(final TestContext context, final String fieldMissing) {

        final JsonObject requestBodyAddCredentials = DeviceRegistryTestUtils.buildCredentialsPayloadPresharedKey(TEST_DEVICE_ID, TEST_AUTH_ID);
        requestBodyAddCredentials.remove(fieldMissing);

        addCredentials(
                requestBodyAddCredentials,
                HttpUtils.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    private static Future<HttpClientResponse> addCredentials(final JsonObject requestPayload) {
        return addCredentials(requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    private static Future<HttpClientResponse> addCredentials(final JsonObject requestPayload, final int expectedStatusCode) {
        return addCredentials(requestPayload, HttpUtils.CONTENT_TYPE_JSON, expectedStatusCode);
    }

    private static Future<HttpClientResponse> addCredentials(final JsonObject requestPayload, final String contentType, final int expectedStatusCode) {

        final Future<HttpClientResponse> result = Future.future();
        final HttpClientRequest req = vertx.createHttpClient().post(getPort(), HOST, URI_ADD_CREDENTIALS)
                .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
                .handler(response -> {
                    if (response.statusCode() == expectedStatusCode) {
                        result.complete(response);
                    } else {
                        result.fail("add credentials failed, expected status code " + expectedStatusCode + " but got " + response.statusCode());
                    }
                }).exceptionHandler(result::fail);

        if (requestPayload == null) {
            req.end();
        } else {
            req.end(requestPayload.encodePrettily());
        }
        return result;
    }

    private static final Future<HttpClientResponse> getCredentials(final String authId, final String type, final Handler<Buffer> responseBodyHandler) {
        return getCredentials(authId, type, HttpURLConnection.HTTP_OK, responseBodyHandler);
    }

    private static final Future<HttpClientResponse> getCredentials(final String authId, final String type, final int expectedStatusCode,
            final Handler<Buffer> responseBodyHandler) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, authId, type);

        vertx.createHttpClient().get(getPort(), HOST, uri).handler(response -> {
            if (response.statusCode() == expectedStatusCode) {
                if (responseBodyHandler != null) {
                    response.bodyHandler(b -> {
                        responseBodyHandler.handle(b);
                        result.complete(response);
                    });
                } else {
                    result.complete(response);
                }
            } else {
                result.fail("expected response [" + expectedStatusCode + "] but got [" + response.statusCode() + "]");
            }
        }).exceptionHandler(result::fail).end();

        return result;
    }

    private static final Future<HttpClientResponse> getCredentials(final String deviceId, final Handler<Buffer> responseBodyHandler) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, deviceId);

        vertx.createHttpClient().get(getPort(), HOST, uri).handler(response -> {
            response.bodyHandler(b -> {
                responseBodyHandler.handle(b);
                result.complete(response);
            });
        }).exceptionHandler(result::fail).end();

        return result;
    }

    private static final Future<HttpClientResponse> updateCredentials(final String authId, final String type, final JsonObject requestPayload) {
        return updateCredentials(authId, type, requestPayload, HttpURLConnection.HTTP_NO_CONTENT);
    }

    private static final Future<HttpClientResponse> updateCredentials(final String authId, final String type, final JsonObject requestPayload, final int expectedResult) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, authId, type);

        final HttpClientRequest req = vertx.createHttpClient().put(getPort(), HOST, uri)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .handler(response -> {
                    if (response.statusCode() == expectedResult) {
                        result.complete(response);
                    } else {
                        result.fail("update credentials failed, expected status code " + expectedResult + " but got " + response.statusCode());
                    }
                })
                .exceptionHandler(result::fail);

        if (requestPayload == null) {
            req.end();
        } else {
            req.end(requestPayload.encodePrettily());
        }
        return result;
    }

    private static final Future<HttpClientResponse> removeCredentials(final String authId, final String type) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, authId, type);

        vertx.createHttpClient().delete(getPort(), HOST, uri).handler(result::complete).exceptionHandler(result::fail).end();
        return result;
    }

    private static final Future<HttpClientResponse> removeCredentials(final String deviceId, final int expectedResponseStatus) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, deviceId);

        vertx.createHttpClient().delete(getPort(), HOST, uri).handler(response -> {
            if (response.statusCode() == expectedResponseStatus) {
                result.complete(response);
            } else {
                result.fail("remove credentials failed, expected response [" + expectedResponseStatus + "] but got [" + response.statusCode() + "]");
            }
        }).exceptionHandler(result::fail).end();
        return result;
    }

}
