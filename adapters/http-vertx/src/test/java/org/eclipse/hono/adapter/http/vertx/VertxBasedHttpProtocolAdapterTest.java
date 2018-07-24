/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.adapter.http.vertx;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
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
import org.eclipse.hono.service.command.CommandResponse;
import org.eclipse.hono.service.command.CommandResponseSender;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link VertxBasedHttpProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedHttpProtocolAdapterTest {

    /**
     * Time out all tests after 10 seconds (some timer based functionality need a slightly higher timeout in
     * slow environments).
     */
    @Rule
    public Timeout timeout = Timeout.seconds(10);
    /**
     * Provides access to the currently running test method.
     */
    @Rule
    public TestName testName = new TestName();

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedHttpProtocolAdapterTest.class);
    private static final String HOST = "127.0.0.1";

    private static HonoClient tenantServiceClient;
    private static HonoClient credentialsServiceClient;
    private static HonoClient messagingClient;
    private static MessageSender telemetrySender;
    private static MessageSender eventSender;
    private static HonoClient registrationServiceClient;
    private static HonoClientBasedAuthProvider usernamePasswordAuthProvider;
    private static HttpProtocolAdapterProperties config;
    private static VertxBasedHttpProtocolAdapter httpAdapter;
    private static CommandConnection commandConnection;
    private static CommandResponseSender commandResponseSender;
    private static Vertx vertx;
    private static String deploymentId;
    private static HttpClient httpClient;

    private static final String syntacticallyCorrectCmdRequestId = "12fcmd-client-c925910f-ea2a-455c-a3f9-a339171f335474f48a55-c60d-4b99-8950-a2fbb9e8f1b6";

    /**
     * Prepare the adapter by configuring it.
     * Since several test cases change the behavior of specific mocked clients, all is created from scratch (and not
     * in a setup method that is invoked once in the class).
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @BeforeClass
    public static void deployAdapter(final TestContext ctx) {
        vertx = Vertx.vertx();

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(tenantServiceClient).shutdown(any(Handler.class));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(credentialsServiceClient));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(credentialsServiceClient).shutdown(any(Handler.class));

        messagingClient = mock(HonoClient.class);
        when(messagingClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(messagingClient).shutdown(any(Handler.class));

        registrationServiceClient = mock(HonoClient.class);
        when(registrationServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(registrationServiceClient));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(registrationServiceClient).shutdown(any(Handler.class));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(commandConnection).shutdown(any(Handler.class));

        commandResponseSender = mock(CommandResponseSender.class);
        when(commandConnection.getOrCreateCommandResponseSender(anyString(), anyString())).thenReturn(
                Future.succeededFuture(commandResponseSender));

        usernamePasswordAuthProvider = mock(HonoClientBasedAuthProvider.class);

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePort(0);
        config.setInsecurePortBindAddress(HOST);
        config.setAuthenticationRequired(true);

        httpAdapter = new VertxBasedHttpProtocolAdapter();
        httpAdapter.setConfig(config);
        httpAdapter.setTenantServiceClient(tenantServiceClient);
        httpAdapter.setCredentialsServiceClient(credentialsServiceClient);
        httpAdapter.setHonoMessagingClient(messagingClient);
        httpAdapter.setRegistrationServiceClient(registrationServiceClient);
        httpAdapter.setCommandConnection(commandConnection);
        httpAdapter.setUsernamePasswordAuthProvider(usernamePasswordAuthProvider);

        vertx.deployVerticle(httpAdapter, ctx.asyncAssertSuccess(id -> {
            deploymentId = id;
            final HttpClientOptions options = new HttpClientOptions()
                    .setDefaultHost(HOST)
                    .setDefaultPort(httpAdapter.getInsecurePort());
            httpClient = vertx.createHttpClient(options);
        }));
    }

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        LOG.info("running test case [{}]", testName.getMethodName());

        final RegistrationClient regClient = mock(RegistrationClient.class);
        when(regClient.assertRegistration(anyString(), any(), (SpanContext) any())).thenReturn(Future.succeededFuture(new JsonObject()));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        final TenantClient tenantClient = mock(TenantClient.class);
        doAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        }).when(tenantClient).get(anyString(), (SpanContext) any());
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        when(commandConnection.getOrCreateCommandConsumer(anyString(), anyString(), any(BiConsumer.class), any(Handler.class))).
                thenReturn(Future.succeededFuture(commandConsumer));

        telemetrySender = mock(MessageSender.class);
        when(telemetrySender.send(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(telemetrySender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(
                Future.succeededFuture(mock(ProtonDelivery.class)));
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(telemetrySender));
        when(messagingClient.getOrCreateTelemetrySender(anyString(), anyString())).thenReturn(Future.succeededFuture(telemetrySender));

        eventSender = mock(MessageSender.class);
        when(eventSender.send(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(eventSender));
        when(messagingClient.getOrCreateEventSender(anyString(), anyString())).thenReturn(Future.succeededFuture(eventSender));

        doAnswer(invocation -> {
            final Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials")));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));
    }

    /**
     * Shuts down the server.
     *
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void finishTest(final TestContext ctx) {
        vertx.undeploy(deploymentId, ctx.asyncAssertSuccess(ok -> vertx.close()));
    }

    /**
     * Verifies that a request to upload telemetry data using POST fails
     * if the request does not contain a Basic <em>Authorization</em> header.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetryFailsForMissingBasicAuthHeader(final TestContext ctx) {

        final Async async = ctx.async();

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .handler(response -> {
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
    @Test
    public void testPostTelemetryFailsForInvalidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .handler(response -> {
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
    public void testPostTelemetrySucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    ctx.assertEquals("*", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a request to upload telemetry data using PUT succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPutTelemetrySucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.put( "/telemetry/DEFAULT_TENANT/device_1")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
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
    public void testPostTelemetryFailsForNotSupportedQoSLevel(final TestContext ctx) {
        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(2))
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());

    }

    /**
     * Verifies that a request (with valid credentials) to upload telemetry data with
     * 'QoS-Level: 1' using POST succeeds with a 202.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetrySucceedsForSupportedQoSLevel(final TestContext ctx) {
        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(1))
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());

    }

    /**
     * Verifies that a request to upload event data using PUT succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPutEventSucceedsForValidCredentials(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.put("/event/DEFAULT_TENANT/device_1")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    ctx.assertEquals("*", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the telemetry URI results in a message that is sent downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetrySendsMessageDownstream(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    verify(telemetrySender).send(any(Message.class), any(SpanContext.class));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the event URI results in a message that is sent downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostEventSendsMessageDownstream(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/event")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    verify(eventSender).send(any(Message.class), any(SpanContext.class));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that the adapter includes a command for the device in the response to
     * a POST request which contains a time-til-disconnect.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testPostTelemetryWithTtdSucceedsWithCommandInResponse(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        // GIVEN an device for which a command is pending
        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        final Message pendingCommand = newMockMessage("DEFAULT_TENANT", "device_1", "doThis");
        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        when(commandConnection.getOrCreateCommandConsumer(anyString(), anyString(), any(BiConsumer.class), any(Handler.class))).
                thenAnswer(invocation -> {
                    final BiConsumer<ProtonDelivery, Message> consumer = invocation.getArgument(2);
                    consumer.accept(mock(ProtonDelivery.class), pendingCommand);
                    return Future.succeededFuture(commandConsumer);
                });
        when(commandConnection.closeCommandConsumer(anyString(), anyString())).
                thenAnswer(invocation -> Future.succeededFuture());

        // WHEN the device posts a telemetry message including a TTD
        httpClient.post("/telemetry?hono-ttd=3")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    // THEN the response contains the pending command
                    ctx.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                    ctx.assertEquals("doThis", response.getHeader(Constants.HEADER_COMMAND));
                    ctx.assertNotNull(response.getHeader(Constants.HEADER_COMMAND_REQUEST_ID));
                    verify(commandConnection).getOrCreateCommandConsumer(eq("DEFAULT_TENANT"), eq("device_1"),
                            any(BiConsumer.class), any(Handler.class));
                    // and the command consumer has been closed again
                    verify(commandConnection).closeCommandConsumer(eq("DEFAULT_TENANT"), eq("device_1"));
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the command reply URI with a misconstructed command-request-id results in a
     * {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForInvalidCommandRequestIdResultsIn400(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=200", "wrongCommandRequestId"))
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the command reply URI with an invalid command status results in a
     * {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForInvalidCommandStatusIdResultsIn400(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=600", syntacticallyCorrectCmdRequestId))
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the command reply URI without a command status results in a
     * {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForMissingCommandStatusIdResultsIn400(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post(String.format("/control/res/%s", syntacticallyCorrectCmdRequestId))
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the command reply URI for which the response message cannot be
     * transferred to the application fails with a 503.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForNotExistingCommandResponseLinkResultsIn503(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        when(commandResponseSender.sendCommandResponse(any(CommandResponse.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST)));

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=200", syntacticallyCorrectCmdRequestId))
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the command reply URI for that the delivery to the associated link is being remotely settled
     * results in a {@link HttpURLConnection#HTTP_ACCEPTED}.

     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForExistingCommandResponseLinkResultsInAccepted(final TestContext ctx) {

        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");
        final ProtonDelivery remotelySettledDelivery = mock(ProtonDelivery.class);
        when(remotelySettledDelivery.remotelySettled()).thenReturn(Boolean.TRUE);

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        when(commandResponseSender.sendCommandResponse(any())).thenReturn(Future.succeededFuture(remotelySettledDelivery));

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=200", syntacticallyCorrectCmdRequestId))
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    // if the delivery was remotely settled, it is to be considered as being successful
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    private static String getBasicAuth(final String user, final String password) {

        final StringBuilder result = new StringBuilder("Basic ");
        result.append(Base64.getEncoder().encodeToString(new StringBuilder(user).append(":").append(password)
                .toString().getBytes(StandardCharsets.UTF_8)));
        return result.toString();
    }

    private static Message newMockMessage(final String tenantId, final String deviceId, final String name) {
        final Message msg = mock(Message.class);
        when(msg.getSubject()).thenReturn(name);
        when(msg.getCorrelationId()).thenReturn("the-correlation-id");
        when(msg.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s", CommandConstants.COMMAND_ENDPOINT,
                tenantId, deviceId, "the-reply-to-id"));
        return msg;
    }

    @SuppressWarnings("unchecked")
    private static void mockSuccessfulAuthentication(final String tenantId, final String deviceId) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture(new Device(tenantId, deviceId)));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));
    }
}

