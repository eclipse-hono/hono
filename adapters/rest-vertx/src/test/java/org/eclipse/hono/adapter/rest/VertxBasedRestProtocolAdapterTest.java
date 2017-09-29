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

package org.eclipse.hono.adapter.rest;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.http.HttpEndpointUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies behavior of {@link VertxBasedRestProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedRestProtocolAdapterTest {

    private static final String HOST = "localhost";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static HonoClient messagingClient;
    private static HonoClient registrationClient;
    private static HonoClientBasedAuthProvider credentialsAuthProvider;
    private static HttpProtocolAdapterProperties config;
    private static VertxBasedRestProtocolAdapter restAdapter;

    private static Vertx vertx;

    @AfterClass
    public final static void shutDown() {
        vertx.close();
    }

    @BeforeClass
    public final static void setup(TestContext context) {
        vertx = Vertx.vertx();

        Future<String> setupTracker = Future.future();
        setupTracker.setHandler(context.asyncAssertSuccess());

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsAuthProvider = mock(HonoClientBasedAuthProvider.class);

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePort(0);
        config.setInsecurePortEnabled(true);
        config.setAuthenticationRequired(true);

        restAdapter = new VertxBasedRestProtocolAdapter();
        restAdapter.setConfig(config);
        restAdapter.setHonoMessagingClient(messagingClient);
        restAdapter.setRegistrationServiceClient(registrationClient);
        restAdapter.setCredentialsAuthProvider(credentialsAuthProvider);

        Future<String> restServerDeploymentTracker = Future.future();
        vertx.deployVerticle(restAdapter, restServerDeploymentTracker.completer());
        restServerDeploymentTracker.compose(c -> setupTracker.complete(), setupTracker);
    }

    @Test
    public final void testBasicAuthFailsEmptyHeader(final TestContext context) {
        final Async async = context.async();

        vertx.createHttpClient().get(restAdapter.getInsecurePort(), HOST, "/somenonexistingroute")
                .putHeader("content-type", HttpEndpointUtils.CONTENT_TYPE_JSON).handler(response -> {
            context.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.statusCode());
            response.bodyHandler(totalBuffer -> {
                async.complete();
            });
        }).exceptionHandler(context::fail).end();
    }

    @SuppressWarnings("unchecked")
    @Test
    public final void testBasicAuthFailsWrongCredentials(final TestContext context) {
        final Async async = context.async();
        final String encodedUserPass = Base64.getEncoder()
                .encodeToString("testuser@DEFAULT_TENANT:password123".getBytes(StandardCharsets.UTF_8));

        doAnswer(invocation -> {
            Handler<AsyncResult<User>> resultHandler = invocation.getArgumentAt(1, Handler.class);
            resultHandler.handle(Future.failedFuture("bad credentials"));
            return null;
        }).when(credentialsAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));

        vertx.createHttpClient().put(restAdapter.getInsecurePort(), HOST, "/somenonexistingroute")
                .putHeader("content-type", HttpEndpointUtils.CONTENT_TYPE_JSON)
                .putHeader(AUTHORIZATION_HEADER, "Basic " + encodedUserPass).handler(response -> {
            context.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.statusCode());
            response.bodyHandler(totalBuffer -> {
                async.complete();
            });
        }).exceptionHandler(context::fail).end();
    }

    @SuppressWarnings("unchecked")
    @Test
    public final void testBasicAuthSuccess(final TestContext context) throws Exception {
        final Async async = context.async();
        final String encodedUserPass = Base64.getEncoder()
                .encodeToString("existinguser@DEFAULT_TENANT:password123".getBytes(StandardCharsets.UTF_8));

        doAnswer(invocation -> {
            Handler<AsyncResult<User>> resultHandler = invocation.getArgumentAt(1, Handler.class);
            resultHandler.handle(Future.succeededFuture(new Device("DEFAULT_TENANT", "device_1")));
            return null;
        }).when(credentialsAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));

        vertx.createHttpClient().get(restAdapter.getInsecurePort(), HOST, "/somenonexistingroute")
                .putHeader("content-type", HttpEndpointUtils.CONTENT_TYPE_JSON)
                .putHeader(AUTHORIZATION_HEADER, "Basic " + encodedUserPass).handler(response -> {
            context.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
            response.bodyHandler(totalBuffer -> {
                async.complete();
            });
        }).exceptionHandler(context::fail).end();
    }
}

