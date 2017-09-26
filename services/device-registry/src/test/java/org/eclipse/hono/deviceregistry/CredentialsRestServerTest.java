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

/**
 * Tests the Credentials REST Interface of the {@link DeviceRegistryRestServer}.
 * Currently limited to the POST method only.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsRestServerTest {

    private static final String HOST = "localhost";

    private static final String TENANT = "testTenant";
    private static final String DEVICE_ID = "testDeviceId";

    private static Vertx vertx;
    private static FileBasedCredentialsService credentialsService;
    private static DeviceRegistryRestServer deviceRegistryRestServer;

    @BeforeClass
    public static void setUp(final TestContext context) {
        vertx = Vertx.vertx();

        Future<String> setupTracker = Future.future();
        setupTracker.setHandler(context.asyncAssertSuccess());

        ServiceConfigProperties restServerProps = new ServiceConfigProperties();
        restServerProps.setInsecurePortEnabled(true);
        restServerProps.setInsecurePort(0);

        CredentialsHttpEndpoint credentialsHttpEndpoint = new CredentialsHttpEndpoint(vertx);
        deviceRegistryRestServer = new DeviceRegistryRestServer();
        deviceRegistryRestServer.addEndpoint(credentialsHttpEndpoint);
        deviceRegistryRestServer.setConfig(restServerProps);

        FileBasedCredentialsConfigProperties credentialsServiceProps = new FileBasedCredentialsConfigProperties();
        credentialsService = new FileBasedCredentialsService();
        credentialsService.setConfig(credentialsServiceProps);

        Future<String> restServerDeploymentTracker = Future.future();
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

        final JsonObject requestBodyAddCredentials = buildCredentialsPayload();

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
        final String requestUri = buildCredentialsPostUri();

        Future<Void> done = Future.future();
        done.setHandler(context.asyncAssertSuccess());

        final JsonObject requestBodyAddCredentials = buildCredentialsPayload();
        Future<Void> addCredentialsFuture = Future.future();
        addCredentials(requestBodyAddCredentials, addCredentialsFuture);

        addCredentialsFuture.compose(ar -> {
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

        final JsonObject requestBodyAddCredentials = buildCredentialsPayload();

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, contentType, requestBodyAddCredentials, expectedStatus);
    }

    /**
     * Verify that an empty json payload to add credentials is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadEmpty(final TestContext context)  {
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
    public void testAddCredentialsWrongJsonPayloadPartsMissingDeviceId(final TestContext context)  {
        final String requestUri = buildCredentialsPostUri();

        final JsonObject requestBodyAddCredentials = buildCredentialsPayload();
        requestBodyAddCredentials.remove(FIELD_DEVICE_ID);

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, CONTENT_TYPE_JSON, requestBodyAddCredentials, expectedStatus);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_TYPE}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadPartsMissingType(final TestContext context)  {
        final String requestUri = buildCredentialsPostUri();

        final JsonObject requestBodyAddCredentials = buildCredentialsPayload();
        requestBodyAddCredentials.remove(FIELD_TYPE);

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, CONTENT_TYPE_JSON, requestBodyAddCredentials, expectedStatus);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_AUTH_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     */
    @Test
    public void testAddCredentialsWrongJsonPayloadPartsMissingAuthId(final TestContext context)  {
        final String requestUri = buildCredentialsPostUri();

        final JsonObject requestBodyAddCredentials = buildCredentialsPayload();
        requestBodyAddCredentials.remove(FIELD_AUTH_ID);

        final Async async = context.async();
        final int expectedStatus = HttpURLConnection.HTTP_BAD_REQUEST;

        postPayloadAndExpectErrorResponse(context, async, requestUri, CONTENT_TYPE_JSON, requestBodyAddCredentials, expectedStatus);
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

    private JsonObject buildCredentialsPayload() {
        final JsonObject secret = new JsonObject().
                put(FIELD_SECRETS_NOT_BEFORE, "2017-05-01T14:00:00+01:00").
                put(FIELD_SECRETS_NOT_AFTER, "2037-06-01T14:00:00+01:00").
                put(FIELD_SECRETS_HASH_FUNCTION, "sha-512").
                put(FIELD_SECRETS_SALT, "aG9ubw==").
                put(FIELD_SECRETS_PWD_HASH, "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA==");
        final JsonObject credPayload = new JsonObject().
                put(FIELD_DEVICE_ID, "4711").
                put(FIELD_TYPE, SECRETS_TYPE_HASHED_PASSWORD).
                put(FIELD_AUTH_ID, "sensor20").
                put(FIELD_SECRETS, new JsonArray().add(secret));
        return credPayload;
    }

    private void addCredentials(final JsonObject requestPayload, final Future<Void> resultFuture) {
        final String requestUri = buildCredentialsPostUri();

        vertx.createHttpClient().post(getPort(), HOST, requestUri).putHeader("Content-Type", CONTENT_TYPE_JSON)
                .handler(response -> {
                    if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                        resultFuture.complete();
                    } else {
                        resultFuture.fail("add credentials failed; response status code: " + response.statusCode());
                    }
                }).exceptionHandler(resultFuture::fail).end(requestPayload.encodePrettily());
    }
}
