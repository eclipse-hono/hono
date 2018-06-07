/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.adapter.http.vertx;

import static org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter.DEFAULT_REOPEN_COMMAND_CONSUMER_TIMEOUT_MILLIS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.BiConsumer;

import io.vertx.proton.ProtonDelivery;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.command.CommandConnection;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.Before;
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
import org.mockito.ArgumentCaptor;

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
    private static CommandConnection commandConnection;


    private static Vertx vertx;

    /**
     * Prepare the adapter by configuring it.
     * Since several test cases change the behaviour of specific mocked clients, all is created from scratch (and not
     * in a setup method that is invoked once in the class).
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Before
    public final void prepareTest(final TestContext ctx) {
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
        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.future());

        registrationServiceClient = mock(HonoClient.class);
        when(registrationServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(registrationServiceClient));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));

        usernamePasswordAuthProvider = mock(HonoClientBasedAuthProvider.class);

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePort(0);
        config.setAuthenticationRequired(true);

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));

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
        httpAdapter.setCommandConnection(commandConnection);
        httpAdapter.setMetrics(mock(HttpAdapterMetrics.class));

        vertx.deployVerticle(httpAdapter, ctx.asyncAssertSuccess());
    }

    /**
     * Shuts down the server.
     */
    @After
    public final void finishTest() {
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
            final Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
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
    @Test
    public final void testPostTelemetrySucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockRegistrationAssertionFailsWith(HttpURLConnection.HTTP_NOT_FOUND);

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

    /**
     * Verifies that a request to upload telemetry data using PUT succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPutTelemetrySucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockRegistrationAssertionFailsWith(HttpURLConnection.HTTP_NOT_FOUND);

        vertx.createHttpClient().put(httpAdapter.getInsecurePort(), HOST, "/telemetry/DEFAULT_TENANT/device_1")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
                    ctx.assertEquals("*", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    /**
     * Verifies that a request (with valid credentials) to upload telemetry data with 'QoS-Level: 2' using POST fails
     * with a 400 (Bad Request) status code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostTelemetryFailsForNotSupportedQoSLevel(final TestContext ctx) {
        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(2))
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
        }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());

    }

    /**
     * Verifies that a request (with valid credentials) to upload telemetry data with
     * 'QoS-Level: 1' using POST succeeds with a 404 (Not Found) status code.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostTelemetrySucceedsForSupportedQoSLevel(final TestContext ctx) {
        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockRegistrationAssertionFailsWith(HttpURLConnection.HTTP_NOT_FOUND);

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(1))
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
                    async.complete();
        }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());

    }

    /**
     * Verifies that a request to upload event data using PUT succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPutEventSucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockRegistrationAssertionFailsWith(HttpURLConnection.HTTP_NOT_FOUND);

        vertx.createHttpClient().put(httpAdapter.getInsecurePort(), HOST, "/event/DEFAULT_TENANT/device_1")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.statusCode());
                    ctx.assertEquals("*", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    /**
     * Verifies that a POST request to the telemetry URI results in a message that is sent downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostTelemetrySendsMessageDownstream(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockSuccessfulDownstreamMessageSender("DEFAULT_TENANT");

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    /**
     * Verifies that a POST request to the event URI results in a message that is sent downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostEventSendsMessageDownstream(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockSuccessfulDownstreamMessageSender("DEFAULT_TENANT");

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/event")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    /**
     * Verifies that a POST request to the telemetry URI with an explicit time-til-disconnect request parameter
     * results in a command receiver link being opened by the adapter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostTelemetryWithTtdOpensCommandReceiver(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockSuccessfulDownstreamMessageSender("DEFAULT_TENANT");

        final CommandConnection testLocalCommandConnection = prepareAdapterWithOwnCommandConsumer();

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry?hono-ttd=1")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    // verify that a command receiver link was opened
                    verify(testLocalCommandConnection).createCommandConsumer(anyString(), anyString(), any(BiConsumer.class), any(Handler.class));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    /**
     * Verifies that a POST request to the telemetry URI with an explicit time-til-disconnect request parameter
     * (resulting in a command receiver link being opened by the adapter) has a close handler attached that
     * reopens the command receiver link (if invoked) again.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public final void testPostTelemetryWithTtdReopensCommandReceiverAfterLinkClose(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        mockSuccessfulDownstreamMessageSender("DEFAULT_TENANT");

        final CommandConnection testLocalCommandConnection = prepareAdapterWithOwnCommandConsumer();

        vertx.createHttpClient().post(httpAdapter.getInsecurePort(), HOST, "/telemetry?hono-ttd=1")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    final ArgumentCaptor<Handler<Void>> handlerArgumentCaptor = ArgumentCaptor.forClass(Handler.class);
                    // verify that a command receiver link was opened
                    verify(testLocalCommandConnection).createCommandConsumer(anyString(), anyString(), any(BiConsumer.class), handlerArgumentCaptor.capture());
                    final Handler  closeHandler = handlerArgumentCaptor.getValue();
                    ctx.assertNotNull(closeHandler);
                    closeHandler.handle(null);
                    vertx.setTimer(DEFAULT_REOPEN_COMMAND_CONSUMER_TIMEOUT_MILLIS * 2, delay -> {
                        // after the reopen timeout, verify that a command receiver link was opened again (resulting in two times)
                        verify(testLocalCommandConnection, times(2)).createCommandConsumer(anyString(), anyString(), any(BiConsumer.class), handlerArgumentCaptor.capture());
                        async.complete();
                    });
                }).exceptionHandler(ctx::fail).end(new JsonObject().encodePrettily());
    }

    private void mockSuccessfulDownstreamMessageSender(final String tenant) {

        final RegistrationClient regClient = mock(RegistrationClient.class);
        when(regClient.assertRegistration(anyString(), any())).thenReturn(Future.succeededFuture(new JsonObject()));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        final TenantClient tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString())).thenReturn(Future.succeededFuture(TenantObject.from(tenant, true)));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        final ProtonDelivery delivery = mock(ProtonDelivery.class);

        final MessageSender telemetrySender = mock(MessageSender.class);
        when(telemetrySender.send(any(Message.class))).thenReturn(Future.succeededFuture(delivery));
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(telemetrySender));

        final MessageSender eventSender = mock(MessageSender.class);
        when(eventSender.send(any(Message.class))).thenReturn(Future.succeededFuture(delivery));
        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(eventSender));
    }

    private static String getBasicAuth(final String user, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        result.append(Base64.getEncoder().encodeToString(new StringBuilder(user).append(":").append(password)
                .toString().getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static void mockSuccessfulAuthentication(final String tenantId, final String deviceId) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture(new Device(tenantId, deviceId)));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));
    }

    private void mockRegistrationAssertionFailsWith(final int statusCode) {
        final RegistrationClient regClient = mock(RegistrationClient.class);
        when(regClient.assertRegistration(anyString(), any())).thenReturn(Future.failedFuture(new ClientErrorException(statusCode)));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));
    }

    private CommandConnection prepareAdapterWithOwnCommandConsumer() {
        final CommandConnection commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));
        httpAdapter.setCommandConnection(commandConnection);

        final MessageConsumer messageConsumer = mock(MessageConsumer.class);
        when(commandConnection.createCommandConsumer(anyString(), anyString(), any(BiConsumer.class), any(Handler.class))).
                thenReturn(Future.succeededFuture(messageConsumer));
        return commandConnection;
    }
}

