/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.client.predicate.ResponsePredicateResult;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link VertxBasedHttpProtocolAdapter}.
 *
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class VertxBasedHttpProtocolAdapterTest {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedHttpProtocolAdapterTest.class);
    private static final String HOST = "127.0.0.1";
    private static final String CMD_REQ_ID = "12fcmd-client-c925910f-ea2a-455c-a3f9-a339171f335474f48a55-c60d-4b99-8950-a2fbb9e8f1b6";

    private TenantClientFactory tenantClientFactory;
    private CredentialsClientFactory credentialsClientFactory;
    private DownstreamSenderFactory downstreamSenderFactory;
    private DownstreamSender telemetrySender;
    private DownstreamSender eventSender;
    private RegistrationClientFactory registrationClientFactory;
    private HonoClientBasedAuthProvider<UsernamePasswordCredentials> usernamePasswordAuthProvider;
    private HttpProtocolAdapterProperties config;
    private VertxBasedHttpProtocolAdapter httpAdapter;
    private ProtocolAdapterCommandConsumerFactory commandConsumerFactory;
    private CommandResponseSender commandResponseSender;
    private DeviceConnectionClientFactory deviceConnectionClientFactory;
    private CommandTargetMapper commandTargetMapper;
    private Vertx vertx;
    private WebClient httpClient;

    /**
     * Prepare the adapter by configuring it.
     * Since several test cases change the behavior of specific mocked clients, all is created from scratch (and not
     * in a setup method that is invoked once in the class).
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @BeforeAll
    public void deployAdapter(final VertxTestContext ctx) {

        vertx = Vertx.vertx();

        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(tenantClientFactory).disconnect(any(Handler.class));

        credentialsClientFactory = mock(CredentialsClientFactory.class);
        when(credentialsClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(credentialsClientFactory).disconnect(any(Handler.class));

        downstreamSenderFactory = mock(DownstreamSenderFactory.class);
        when(downstreamSenderFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(downstreamSenderFactory).disconnect(any(Handler.class));

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(registrationClientFactory).disconnect(any(Handler.class));

        commandConsumerFactory = mock(ProtocolAdapterCommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(commandConsumerFactory).disconnect(any(Handler.class));

        deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> shutdownHandler = invocation.getArgument(0);
            shutdownHandler.handle(Future.succeededFuture());
            return null;
        }).when(deviceConnectionClientFactory).disconnect(any(Handler.class));

        commandTargetMapper = mock(CommandTargetMapper.class);

        commandResponseSender = mock(CommandResponseSender.class);
        when(commandConsumerFactory.getCommandResponseSender(anyString(), anyString()))
            .thenReturn(Future.succeededFuture(commandResponseSender));

        usernamePasswordAuthProvider = mock(HonoClientBasedAuthProvider.class);

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePort(0);
        config.setInsecurePortBindAddress(HOST);
        config.setAuthenticationRequired(true);

        httpAdapter = new VertxBasedHttpProtocolAdapter();
        httpAdapter.setConfig(config);
        httpAdapter.setTenantClientFactory(tenantClientFactory);
        httpAdapter.setCredentialsClientFactory(credentialsClientFactory);
        httpAdapter.setDownstreamSenderFactory(downstreamSenderFactory);
        httpAdapter.setRegistrationClientFactory(registrationClientFactory);
        httpAdapter.setCommandConsumerFactory(commandConsumerFactory);
        httpAdapter.setDeviceConnectionClientFactory(deviceConnectionClientFactory);
        httpAdapter.setCommandTargetMapper(commandTargetMapper);
        httpAdapter.setUsernamePasswordAuthProvider(usernamePasswordAuthProvider);

        vertx.deployVerticle(httpAdapter, ctx.succeeding(deploymentId -> {
            final WebClientOptions options = new WebClientOptions()
                    .setDefaultHost(HOST)
                    .setDefaultPort(httpAdapter.getInsecurePort());
            httpClient = WebClient.create(vertx, options);
            ctx.completeNow();
        }));
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo The test meta data.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp(final TestInfo testInfo) {

        LOG.info("running test case [{}]", testInfo.getDisplayName());

        final RegistrationClient regClient = mock(RegistrationClient.class);
        when(regClient.assertRegistration(anyString(), any(), (SpanContext) any())).thenReturn(Future.succeededFuture(new JsonObject()));
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        final TenantClient tenantClient = mock(TenantClient.class);
        doAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        }).when(tenantClient).get(anyString(), (SpanContext) any());
        doAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        }).when(tenantClient).get(anyString());
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        final ProtocolAdapterCommandConsumer commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), any(Handler.class), any(), any())).
                thenReturn(Future.succeededFuture(commandConsumer));

        telemetrySender = mock(DownstreamSender.class);
        when(telemetrySender.send(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(telemetrySender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(
                Future.succeededFuture(mock(ProtonDelivery.class)));
        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(telemetrySender));

        eventSender = mock(DownstreamSender.class);
        when(eventSender.send(any(Message.class), (SpanContext) any())).thenThrow(new UnsupportedOperationException());
        when(eventSender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(downstreamSenderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(eventSender));

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
    @AfterAll
    public void finishTest(final VertxTestContext ctx) {
        vertx.close(ctx.completing());
    }

    /**
     * Verifies that a request to upload telemetry data using POST fails
     * if the request does not contain a Basic <em>Authorization</em> header.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetryFailsForMissingBasicAuthHeader(final VertxTestContext ctx) {

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED))
                .send(ctx.completing());
    }

    /**
     * Verifies that a request to upload telemetry data using POST fails
     * if the request contains a Basic <em>Authorization</em> header with
     * invalid credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetryFailsForInvalidCredentials(final VertxTestContext ctx) {

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_UNAUTHORIZED))
                .send(ctx.completing());
    }

    /**
     * Verifies that a request to upload telemetry data using POST fails
     * with a 503 status code if the credentials on record cannot be retrieved.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testPostTelemetryFailsForUnreachableCredentialsService(final VertxTestContext ctx) {

        doAnswer(invocation -> {
            final Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, "service down")));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_UNAVAILABLE))
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a request to upload telemetry data using POST succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetrySucceedsForValidCredentials(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .expect(this::assertCorsHeaders)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a request to upload telemetry data using PUT succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPutTelemetrySucceedsForValidCredentials(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.put("/telemetry/DEFAULT_TENANT/device_1")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .expect(this::assertCorsHeaders)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a request (with valid credentials) to upload telemetry data with 'QoS-Level: 2' using POST fails
     * with a 400 (Bad Request) status code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetryFailsForNotSupportedQoSLevel(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(2))
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_BAD_REQUEST))
                .sendJsonObject(new JsonObject(), ctx.completing());

    }

    /**
     * Verifies that a request (with valid credentials) to upload telemetry data with
     * 'QoS-Level: 1' using POST succeeds with a 202.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetrySucceedsForQoS1(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .putHeader(Constants.HEADER_QOS_LEVEL, String.valueOf(1))
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .expect(this::assertCorsHeaders)
                .sendJsonObject(new JsonObject(), ctx.succeeding(r -> {
                    ctx.verify(() -> verify(telemetrySender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class)));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a request to upload event data using PUT succeeds
     * if the request contains a Basic <em>Authorization</em> header with valid
     * credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPutEventSucceedsForValidCredentials(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.put("/event/DEFAULT_TENANT/device_1")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .expect(this::assertCorsHeaders)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a POST request to the telemetry URI results in a message that is sent downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostTelemetrySendsMessageDownstream(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/telemetry")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .expect(this::assertCorsHeaders)
                .sendJsonObject(new JsonObject(), ctx.succeeding(r -> {
                    ctx.verify(() -> verify(telemetrySender).send(any(Message.class), any(SpanContext.class)));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a POST request to the event URI results in a message that is sent downstream.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostEventSendsMessageDownstream(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post("/event")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .expect(this::assertCorsHeaders)
                .sendJsonObject(new JsonObject(), ctx.succeeding(r -> {
                    ctx.verify(() -> verify(eventSender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class)));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the adapter includes a command for the device in the response to
     * a POST request which contains a time-til-disconnect.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testPostTelemetryWithTtdSucceedsWithCommandInResponse(final VertxTestContext ctx) {

        // GIVEN an device for which a command is pending
        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");
        final Message msg = newMockMessage("DEFAULT_TENANT", "device_1", "doThis");
        final Command pendingCommand = Command.from(msg, "DEFAULT_TENANT", "device_1");
        final CommandContext commandContext = CommandContext.from(pendingCommand, mock(ProtonDelivery.class), mock(Span.class));
        final ProtocolAdapterCommandConsumer commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(eq("DEFAULT_TENANT"), eq("device_1"), any(Handler.class), any(), any()))
                .thenAnswer(invocation -> {
                    final Handler<CommandContext> consumer = invocation.getArgument(2);
                    consumer.handle(commandContext);
                    return Future.succeededFuture(commandConsumer);
                });

        // WHEN the device posts a telemetry message including a TTD
        httpClient.post("/telemetry")
                .addQueryParam("hono-ttd", "3")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                // THEN the response contains the pending command
                .expect(ResponsePredicate.SC_OK)
                .expect(this::assertCorsHeaders)
                .expect(response -> {
                    if (!"doThis".equals(response.getHeader(Constants.HEADER_COMMAND))) {
                        return ResponsePredicateResult.failure("response does not contain expected hono-command header");
                    }
                    if (response.getHeader(Constants.HEADER_COMMAND_REQUEST_ID) == null) {
                        return ResponsePredicateResult.failure("response does not contain hono-cmd-req-id header");
                    }
                    return ResponsePredicateResult.success();
                })
                .sendJsonObject(new JsonObject(), ctx.succeeding(r -> {
                    ctx.verify(() -> {
                        verify(commandConsumerFactory).createCommandConsumer(eq("DEFAULT_TENANT"), eq("device_1"),
                                any(Handler.class), any(), any());
                        // and the command consumer has been closed again
                        verify(commandConsumer).close(any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a POST request to the command reply URI with a malformed command-request-id results in a
     * {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForInvalidCommandRequestIdResultsIn400(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post(getCommandResponsePath("wrongCommandRequestId"))
                .addQueryParam(Constants.HEADER_COMMAND_RESPONSE_STATUS, "200")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.SC_BAD_REQUEST)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a POST request to the command reply URI with an invalid command status results in a
     * {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForInvalidCommandStatusIdResultsIn400(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post(getCommandResponsePath(CMD_REQ_ID))
                .addQueryParam(Constants.HEADER_COMMAND_RESPONSE_STATUS, "600")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.SC_BAD_REQUEST)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a POST request to the command reply URI without a command status results in a
     * {@link HttpURLConnection#HTTP_BAD_REQUEST}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForMissingCommandStatusIdResultsIn400(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        httpClient.post(getCommandResponsePath(CMD_REQ_ID))
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.SC_BAD_REQUEST)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a POST request to the command reply URI for which the response message cannot be
     * transferred to the application fails with a 503.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForNotExistingCommandResponseLinkResultsIn503(final VertxTestContext ctx) {

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        when(commandResponseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));

        httpClient.post(getCommandResponsePath(CMD_REQ_ID))
                .addQueryParam(Constants.HEADER_COMMAND_RESPONSE_STATUS, "200")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.SC_SERVICE_UNAVAILABLE)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    /**
     * Verifies that a POST request to the command reply URI for that the delivery to the associated link is being remotely settled
     * results in a {@link HttpURLConnection#HTTP_ACCEPTED}.

     * @param ctx The vert.x test context.
     */
    @Test
    public void testPostCmdResponseForExistingCommandResponseLinkResultsInAccepted(final VertxTestContext ctx) {

        final ProtonDelivery remotelySettledDelivery = mock(ProtonDelivery.class);
        when(remotelySettledDelivery.remotelySettled()).thenReturn(Boolean.TRUE);

        mockSuccessfulAuthentication("DEFAULT_TENANT", "device_1");

        when(commandResponseSender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(
                Future.succeededFuture(remotelySettledDelivery));

        httpClient.post(getCommandResponsePath(CMD_REQ_ID))
                .addQueryParam(Constants.HEADER_COMMAND_RESPONSE_STATUS, "200")
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpUtils.CONTENT_TYPE_JSON)
                .basicAuthentication("testuser@DEFAULT_TENANT", "password123")
                .putHeader(HttpHeaders.ORIGIN.toString(), "hono.eclipse.org")
                .expect(ResponsePredicate.SC_ACCEPTED)
                .sendJsonObject(new JsonObject(), ctx.completing());
    }

    private String getCommandResponsePath(final String wrongCommandRequestId) {
        return String.format("/%s/res/%s", CommandConstants.COMMAND_ENDPOINT, wrongCommandRequestId);
    }

    private static Message newMockMessage(final String tenantId, final String deviceId, final String name) {
        final Message msg = mock(Message.class);
        when(msg.getAddress()).thenReturn(String.format("%s/%s/%s",
                CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId));
        when(msg.getSubject()).thenReturn(name);
        when(msg.getCorrelationId()).thenReturn("the-correlation-id");
        when(msg.getReplyTo()).thenReturn(String.format("%s/%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT,
                tenantId, deviceId, "the-reply-to-id"));
        return msg;
    }

    @SuppressWarnings("unchecked")
    private void mockSuccessfulAuthentication(final String tenantId, final String deviceId) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<User>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture(new DeviceUser(tenantId, deviceId)));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(JsonObject.class), any(Handler.class));
    }

    private ResponsePredicateResult assertCorsHeaders(final HttpResponse<?> response) {
        final MultiMap headers = response.headers();
        final String exposedHeaders = headers.get(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        if (exposedHeaders == null) {
            return ResponsePredicateResult.failure("response does not contain Access-Control-Expose-Headers header");
        }
        if (!exposedHeaders.contains(Constants.HEADER_COMMAND)) {
            return ResponsePredicateResult.failure("Access-Control-Expose-Headers does not include hono-command");
        }
        if (!exposedHeaders.contains(Constants.HEADER_COMMAND_REQUEST_ID)) {
            return ResponsePredicateResult.failure("Access-Control-Expose-Headers does not include hono-cmd-req-id");
        }
        if (!"*".equals(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))) {
            return ResponsePredicateResult.failure("response does not contain proper Access-Control-Allow-Origin header");
        }
        return ResponsePredicateResult.success();
    }
}
