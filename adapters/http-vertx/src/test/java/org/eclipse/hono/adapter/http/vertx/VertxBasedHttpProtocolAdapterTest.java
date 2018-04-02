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

package org.eclipse.hono.adapter.http.vertx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.http.HttpUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Verifies behavior of {@link VertxBasedHttpProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedHttpProtocolAdapterTest {

    /**
     * Time out all tests after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private static final String HOST = "localhost";

    private static HonoClient tenantServiceClient;
    private static HonoClient credentialsServiceClient;
    private static HonoClient messagingClient;
    private static HonoClient registrationServiceClient;
    private static HonoClientBasedAuthProvider usernamePasswordAuthProvider;
    private static HttpProtocolAdapterProperties config;
    private static VertxBasedHttpProtocolAdapter httpAdapter;

    private static Vertx vertx;

    /**
     * Sets up the protocol adapter.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @BeforeClass
    public static final void setup(final TestContext ctx) {

        vertx = Vertx.vertx();

        final TenantClient tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString())).thenReturn(Future.future());

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(credentialsServiceClient));

        messagingClient = mock(HonoClient.class);
        when(messagingClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.future());

        registrationServiceClient = mock(HonoClient.class);
        when(registrationServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(registrationServiceClient));

        usernamePasswordAuthProvider = mock(HonoClientBasedAuthProvider.class);

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePort(0);
        config.setAuthenticationRequired(true);

        httpAdapter = new VertxBasedHttpProtocolAdapter();
        httpAdapter.setConfig(config);
        httpAdapter.setTenantServiceClient(tenantServiceClient);
        httpAdapter.setHonoMessagingClient(messagingClient);
        httpAdapter.setRegistrationServiceClient(registrationServiceClient);
        httpAdapter.setCredentialsServiceClient(credentialsServiceClient);
        httpAdapter.setUsernamePasswordAuthProvider(usernamePasswordAuthProvider);

        vertx.deployVerticle(httpAdapter, ctx.asyncAssertSuccess());
    }

    /**
     * Shuts down the server.
     */
    @AfterClass
    public static final void shutDown() {
        vertx.close();
    }

    /**
     * Verifies that a request to upload telemetry data using POST fails
     * if the request does not contain a Basic <em>Authorization</em> header.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostTelemetryFailsForMissingBasicAuthHeader(final TestContext ctx) {

        final Async async = ctx.async();

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON).handler(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.statusCode());
            async.complete();
        }).exceptionHandler(ctx::fail).end();
    }

    /**
     * Verifies that a request to upload telemetry data using POST fails
     * if the request contains a Basic <em>Authorization</em> header with
     * invalid credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public final void testPostTelemetryFailsForInvalidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        doAnswer(invocation -> {
            Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials")));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader).handler(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.statusCode());
            async.complete();
        }).exceptionHandler(ctx::fail).end();
    }

    /**
     * Verifies that a request to upload telemetry data using POST succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public final void testPostTelemetrySucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        doAnswer(invocation -> {
            Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture(new Device("DEFAULT_TENANT", "device_1")));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));

        final RegistrationClient regClient = mock(RegistrationClient.class);
        when(regClient.assertRegistration(anyString(), any())).thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
            ctx.assertEquals("*", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            async.complete();
        }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    private static String getBasicAuth(final String user, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        result.append(Base64.getEncoder().encodeToString(new StringBuilder(user).append(":").append(password)
                .toString().getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }
}

