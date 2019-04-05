/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.http.impl;

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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
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

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
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
import io.vertx.proton.ProtonReceiver;

/**
 * Verifies behavior of {@link VertxBasedHttpProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedHttpProtocolAdapterTest {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedHttpProtocolAdapterTest.class);
    private static final String HOST = "127.0.0.1";
    private static final String CMD_REQ_ID = "12fcmd-client-c925910f-ea2a-455c-a3f9-a339171f335474f48a55-c60d-4b99-8950-a2fbb9e8f1b6";

    private static TenantClientFactory tenantClientFactory;
    private static HonoClient credentialsServiceClient;
    private static HonoClient messagingClient;
    private static MessageSender telemetrySender;
    private static MessageSender eventSender;
    private static RegistrationClientFactory registrationClientFactory;
    private static HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private static HttpProtocolAdapterProperties config;
    private static VertxBasedHttpProtocolAdapter httpAdapter;
    private static CommandConsumerFactory commandConsumerFactory;
    private static CommandResponseSender commandResponseSender;
    private static Vertx vertx;
    private static String deploymentId;
    private static HttpClient httpClient;

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

        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoClient.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(tenantClientFactory).disconnect(any(Handler.class));

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

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoClient.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(registrationClientFactory).disconnect(any(Handler.class));

        commandConsumerFactory = mock(CommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoClient.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(commandConsumerFactory).disconnect(any(Handler.class));

        commandResponseSender = mock(CommandResponseSender.class);
        when(commandConsumerFactory.getCommandResponseSender(anyString(), anyString())).thenReturn(
                Future.succeededFuture(commandResponseSender));

        usernamePasswordAuthProvider = mock(HonoClientBasedAuthProvider.class);

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePort(0);
        config.setInsecurePortBindAddress(HOST);
        config.setAuthenticationRequired(true);

        httpAdapter = new VertxBasedHttpProtocolAdapter();
        httpAdapter.setConfig(config);
        httpAdapter.setTenantClientFactory(tenantClientFactory);
        httpAdapter.setCredentialsServiceClient(credentialsServiceClient);
        httpAdapter.setHonoMessagingClient(messagingClient);
        httpAdapter.setRegistrationClientFactory(registrationClientFactory);
        httpAdapter.setCommandConsumerFactory(commandConsumerFactory);
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
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        final TenantClient tenantClient = mock(TenantClient.class);
        doAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        }).when(tenantClient).get(anyString(), (SpanContext) any());
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> resultHandler = invocation.getArgument(0);
            if (resultHandler != null) {
                resultHandler.handle(Future.succeededFuture());
            }
            return null;
        }).when(commandConsumer).close(any(Handler.class));
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), any(Handler.class), any(Handler.class))).
                thenReturn(Future.succeededFuture(commandConsumer));

        telemetrySender = mock(MessageSender.class);
        when(telemetrySender.send(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(telemetrySender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(
                Future.succeededFuture(mock(ProtonDelivery.class)));
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(telemetrySender));

        eventSender = mock(MessageSender.class);
        when(eventSender.send(any(Message.class), (SpanContext) any())).thenThrow(new UnsupportedOperationException());
        when(eventSender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(eventSender));

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
                    assertCorsHeaders(ctx, response.headers());
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
                    assertCorsHeaders(ctx, response.headers());
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
    public void testPostTelemetrySucceedsForQoS1(final TestContext ctx) {
        final Async async = ctx.async();
        final String authHeader = getBasicAuth("testuser@DEFAULT_TENANT", "password123");

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(1))
                .handler(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_ACCEPTED, response.statusCode());
                    assertCorsHeaders(ctx, response.headers());
                    verify(telemetrySender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class));
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
                    assertCorsHeaders(ctx, response.headers());
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
                    assertCorsHeaders(ctx, response.headers());
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
                    assertCorsHeaders(ctx, response.headers());
                    verify(eventSender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class));
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
        final Message msg = newMockMessage("DEFAULT_TENANT", "device_1", "doThis");
        final Command pendingCommand = Command.from(msg, "DEFAULT_TENANT", "device_1");
        final CommandContext commandContext = CommandContext.from(pendingCommand, mock(ProtonDelivery.class), mock(ProtonReceiver.class), mock(Span.class));
        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        when(commandConsumerFactory.createCommandConsumer(eq("DEFAULT_TENANT"), eq("device_1"), any(Handler.class), any(Handler.class))).
                thenAnswer(invocation -> {
                    final Handler<CommandContext> consumer = invocation.getArgument(2);
                    consumer.handle(commandContext);
                    return Future.succeededFuture(commandConsumer);
                });

        // WHEN the device posts a telemetry message including a TTD
        httpClient.post("/telemetry?hono-ttd=3")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.ORIGIN, "hono.eclipse.org")
                .handler(response -> {
                    // THEN the response contains the pending command
                    ctx.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode());
                    assertCorsHeaders(ctx, response.headers());
                    ctx.assertEquals("doThis", response.getHeader(Constants.HEADER_COMMAND));
                    ctx.assertNotNull(response.getHeader(Constants.HEADER_COMMAND_REQUEST_ID));
                    verify(commandConsumerFactory).createCommandConsumer(eq("DEFAULT_TENANT"), eq("device_1"),
                            any(Handler.class), any(Handler.class));
                    // and the command consumer has been closed again
                    verify(commandConsumer).close(any());
                    async.complete();
                }).exceptionHandler(ctx::fail).end(new JsonObject().encode());
    }

    /**
     * Verifies that a POST request to the command reply URI with a malformed command-request-id results in a
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

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=600", CMD_REQ_ID))
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

        httpClient.post(String.format("/control/res/%s", CMD_REQ_ID))
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

        when(commandResponseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=200", CMD_REQ_ID))
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

        when(commandResponseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(
                Future.succeededFuture(remotelySettledDelivery));

        httpClient.post(String.format("/control/res/%s?hono-cmd-status=200", CMD_REQ_ID))
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
            resultHandler.handle(Future.succeededFuture(new DeviceUser(tenantId, deviceId)));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));
    }

    private void assertCorsHeaders(final TestContext ctx, final MultiMap headers) {
        final String exposedHeaders = headers.get(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        ctx.assertNotNull(exposedHeaders);
        ctx.assertTrue(exposedHeaders.contains(Constants.HEADER_COMMAND));
        ctx.assertTrue(exposedHeaders.contains(Constants.HEADER_COMMAND_REQUEST_ID));
        ctx.assertEquals("*", headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}

