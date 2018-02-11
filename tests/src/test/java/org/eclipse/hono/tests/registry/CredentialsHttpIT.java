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
package org.eclipse.hono.tests.registry;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Credentials HTTP endpoint and validating the corresponding responses.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsHttpIT {

    private static final String TENANT = Constants.DEFAULT_TENANT;
    private static final String URI_ADD_CREDENTIALS = "/" + CredentialsConstants.CREDENTIALS_ENDPOINT + "/" + TENANT;
    private static final String TEMPLATE_URI_CREDENTIALS_INSTANCE = String.format("/%s/%s/%%s/%%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT);
    private static final String TEMPLATE_URI_CREDENTIALS_BY_DEVICE = String.format("/%s/%s/%%s", CredentialsConstants.CREDENTIALS_ENDPOINT, TENANT);
    private static final String TEST_AUTH_ID = "sensor20";
    private static final Vertx vertx = Vertx.vertx();

    private static CrudHttpClient httpClient;

    /**
     * Time out each test after 5 secs.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private String deviceId;
    private String authId;
    private JsonObject hashedPasswordCredentials;
    private JsonObject pskCredentials;

    /**
     * Creates the HTTP client for accessing the registry.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUpClient(final TestContext ctx) {

        httpClient = new CrudHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        deviceId = UUID.randomUUID().toString();
        authId = getRandomAuthId(TEST_AUTH_ID);
        hashedPasswordCredentials = newHashedPasswordCredentials(deviceId, authId);
        pskCredentials = newPskCredentials(deviceId, authId);
    }

    /**
     * Removes the credentials that have been added by the test.
     * 
     * @param ctx The vert.x test context.
     */
    @After
    public void removeCredentials(final TestContext ctx) {
        final Async deletion = ctx.async();
        removeCredentials(deviceId, HttpURLConnection.HTTP_NO_CONTENT).setHandler(attempt -> deletion.complete());
        deletion.await();
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
     * Verifies that the service accepts an add credentials request containing valid credentials
     * and that the response contains a <em>Location</em> header for the created resource.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceeds(final TestContext context)  {

        addCredentials(hashedPasswordCredentials).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly filled json payload to add credentials for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsRejectsDuplicateRegistration(final TestContext context)  {

        addCredentials(hashedPasswordCredentials).compose(ar -> {
            // now try to add credentials again
            return addCredentials(hashedPasswordCredentials, HttpURLConnection.HTTP_CONFLICT);
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

        addCredentials(
                hashedPasswordCredentials,
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

        addCredentials(null, HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
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

    private void testAddCredentialsWithMissingPayloadParts(final TestContext context, final String fieldMissing) {

        hashedPasswordCredentials.remove(fieldMissing);

        addCredentials(
                hashedPasswordCredentials,
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsSucceeds(final TestContext context) {

        final JsonObject altered = hashedPasswordCredentials.copy();
        altered.put(CredentialsConstants.FIELD_DEVICE_ID, "other-device");

        addCredentials(hashedPasswordCredentials)
            .compose(ar -> updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, altered))
            .compose(ur -> getCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
            .setHandler(context.asyncAssertSuccess(gr -> {
                context.assertEquals("other-device", gr.toJsonObject().getString(CredentialsConstants.FIELD_DEVICE_ID));
            }));
    }

    /**
     * Verifies that the service rejects an update request for non-existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonExistingCredentials(final TestContext context) {

        updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, hashedPasswordCredentials, HttpURLConnection.HTTP_NOT_FOUND)
            .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service rejects an update request for credentials containing a different type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonMatchingTypeInPayload(final TestContext context) {

        final JsonObject altered = hashedPasswordCredentials.copy().put(CredentialsConstants.FIELD_TYPE, "non-matching-type");

        addCredentials(hashedPasswordCredentials).compose(ar -> {
            return updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, altered, HttpURLConnection.HTTP_BAD_REQUEST);
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

        final JsonObject altered = hashedPasswordCredentials.copy().put(CredentialsConstants.FIELD_AUTH_ID, "non-matching-auth-id");

        addCredentials(hashedPasswordCredentials).compose(ar -> {
            return updateCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, altered, HttpURLConnection.HTTP_BAD_REQUEST);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can be successfully deleted again by using the device-id.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsForDeviceSucceeds(final TestContext context) {

        addCredentials(hashedPasswordCredentials).compose(ar -> {
            return removeCredentials(deviceId, HttpURLConnection.HTTP_NO_CONTENT);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that a correctly added credentials record can be successfully deleted again by using the type and authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsSucceeds(final TestContext context) {

        addCredentials(hashedPasswordCredentials).compose(ar -> {
            // now try to remove credentials again
            return removeCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can not be deleted by using the correct authId but a non matching type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveCredentialsFailsForWrongType(final TestContext context) {

        addCredentials(hashedPasswordCredentials).compose(ar -> {
            // now try to remove credentials again
            return removeCredentials(authId, "wrong-type");
        }).setHandler(context.asyncAssertFailure());
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

        addCredentials(hashedPasswordCredentials)
            .compose(ar -> getCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
            .setHandler(context.asyncAssertSuccess(b -> {
                context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(b.toJsonObject(), hashedPasswordCredentials));
            }));
    }

    /**
     * Verify that multiple (2) correctly added credentials records of the same authId can be successfully looked up by single
     * requests using their type and authId again.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsMultipleTypesSingleRequests(final TestContext context) {

        final List<JsonObject> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(hashedPasswordCredentials);
        credentialsListToAdd.add(pskCredentials);

        addMultipleCredentials(credentialsListToAdd)
            .compose(ar -> getCredentials(authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
            .compose(hashedPwdSecret -> {
                context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(hashedPwdSecret.toJsonObject(), hashedPasswordCredentials));
                return getCredentials(authId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY);
            }).setHandler(context.asyncAssertSuccess(pskSecret -> {
                context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(pskSecret.toJsonObject(), pskCredentials));
            }));
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
        credentialsListToAdd.add(newPskCredentials(deviceId, "auth"));
        credentialsListToAdd.add(newPskCredentials(deviceId, "other-auth"));

        addMultipleCredentials(credentialsListToAdd)
            .compose(ar -> getCredentials(deviceId))
            .setHandler(context.asyncAssertSuccess(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonObject(), credentialsListToAdd);
            }));
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

        final String pskAuthId = getRandomAuthId(TEST_AUTH_ID);
        final List<JsonObject> credentialsToAdd = new ArrayList<>();
        for(int i = 0; i < 5; i++) {
            final JsonObject requestBody = newPskCredentials(deviceId, pskAuthId);
            requestBody.put(CredentialsConstants.FIELD_TYPE, "type" + i);
            credentialsToAdd.add(requestBody);
        }
        addMultipleCredentials(credentialsToAdd)
            .compose(ar -> getCredentials(deviceId))
            .setHandler(context.asyncAssertSuccess(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonObject(), credentialsToAdd);
            }));
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongType(final TestContext context)  {

        addCredentials(hashedPasswordCredentials)
            .compose(ar -> getCredentials(authId, "wrong-type", HttpURLConnection.HTTP_NOT_FOUND))
            .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongAuthId(final TestContext context)  {

        addCredentials(hashedPasswordCredentials)
            .compose(ar -> getCredentials("wrong-auth-id", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, HttpURLConnection.HTTP_NOT_FOUND))
            .setHandler(context.asyncAssertSuccess());
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

    private static Future<Void> addCredentials(final JsonObject requestPayload) {
        return addCredentials(requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    private static Future<Void> addCredentials(final JsonObject requestPayload, final int expectedStatusCode) {
        return addCredentials(requestPayload, "application/json", expectedStatusCode);
    }

    private static Future<Void> addCredentials(final JsonObject requestPayload, final String contentType, final int expectedStatusCode) {

        return httpClient.create(URI_ADD_CREDENTIALS, requestPayload, contentType, response -> response.statusCode() == expectedStatusCode);
    }

    private static final Future<Buffer> getCredentials(final String deviceId) {

        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, deviceId);
        return httpClient.get(uri, status -> true);
    }

    private static final Future<Buffer> getCredentials(final String authId, final String type) {
        return getCredentials(authId, type, HttpURLConnection.HTTP_OK);
    }

    private static final Future<Buffer> getCredentials(final String authId, final String type, final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, authId, type);
        return httpClient.get(uri, status -> status == expectedStatusCode);
    }

    private static final Future<Void> updateCredentials(final String authId, final String type, final JsonObject requestPayload) {
        return updateCredentials(authId, type, requestPayload, HttpURLConnection.HTTP_NO_CONTENT);
    }

    private static final Future<Void> updateCredentials(final String authId, final String type, final JsonObject requestPayload, final int expectedResult) {

        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, authId, type);
        return httpClient.update(uri, requestPayload, status -> status == expectedResult);
    }

    private static final Future<Void> removeCredentials(final String authId, final String type) {

        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_INSTANCE, authId, type);
        return httpClient.delete(uri, status -> status == HttpURLConnection.HTTP_NO_CONTENT);
    }

    private static final Future<Void> removeCredentials(final String deviceId, final int expectedResponseStatus) {

        final String uri = String.format(TEMPLATE_URI_CREDENTIALS_BY_DEVICE, deviceId);
        return httpClient.delete(uri, status -> status == expectedResponseStatus);
    }

    private static JsonObject newHashedPasswordCredentials(final String deviceId, final String authId) {
        return JsonObject.mapFrom(CredentialsObject.fromHashedPassword(deviceId, authId,
                "secret", "sha-512", null, null, null));
    }

    private static JsonObject newPskCredentials(final String deviceId, final String authId) {
        return JsonObject.mapFrom(CredentialsObject.fromPresharedKey(
                deviceId, authId, "secret".getBytes(StandardCharsets.UTF_8), null, null));
    }
}
