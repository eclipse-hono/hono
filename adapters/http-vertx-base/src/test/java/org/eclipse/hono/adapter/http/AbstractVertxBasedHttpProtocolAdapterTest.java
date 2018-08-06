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

package org.eclipse.hono.adapter.http;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.service.command.Command;
import org.eclipse.hono.service.command.CommandConnection;
import org.eclipse.hono.service.command.CommandConsumer;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link AbstractVertxBasedHttpProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedHttpProtocolAdapterTest {

    private static final String ADAPTER_TYPE = "http";

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    private HonoClient                    credentialsServiceClient;
    private HonoClient                    tenantServiceClient;
    private HonoClient                    messagingClient;
    private HonoClient                    registrationServiceClient;
    private RegistrationClient            regClient;
    private TenantClient                  tenantClient;
    private HttpProtocolAdapterProperties config;
    private CommandConnection             commandConnection;
    private CommandConsumer               commandConsumer;
    private Vertx                         vertx;
    private Context                       context;
    private HttpAdapterMetrics            metrics;

    /**
     * Sets up common fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setup() {

        context = mock(Context.class);
        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });

        config = new HttpProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);

        metrics = mock(HttpAdapterMetrics.class);

        regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
        when(regClient.assertRegistration(anyString(), any(), (SpanContext) any())).thenReturn(Future.succeededFuture(result));

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), (SpanContext) any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(credentialsServiceClient));

        messagingClient = mock(HonoClient.class);
        when(messagingClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));

        registrationServiceClient = mock(HonoClient.class);
        when(registrationServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(registrationServiceClient));
        when(registrationServiceClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));
        when(commandConnection.closeCommandConsumer(anyString(), anyString())).thenReturn(Future.succeededFuture());
        commandConsumer = mock(CommandConsumer.class);
        when(commandConnection.getOrCreateCommandConsumer(anyString(), anyString(), any(BiConsumer.class), any(Handler.class)))
            .thenReturn(Future.succeededFuture(commandConsumer));
    }

    /**
     * Verifies that a client provided HTTP server is started instead of creating and starting a new http server.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartUsesClientProvidedHttpServer(final TestContext ctx) {

        // GIVEN an adapter with a client provided HTTP server
        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the client provided HTTP server has been configured and started
        startup.await();
        verify(server).requestHandler(any(Handler.class));
        verify(server).listen(any(Handler.class));
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is invoked if the
     * HTTP server has been started successfully.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartInvokesOnStartupSuccess(final TestContext ctx) {

        // GIVEN an adapter with a client provided http server
        final HttpServer server = getHttpServer(false);
        final Async onStartupSuccess = ctx.async();

        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server,
                s -> onStartupSuccess.complete());

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
        startup.await();
        onStartupSuccess.await();
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if a
     * client provided HTTP server fails to start.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final TestContext ctx) {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        final HttpServer server = getHttpServer(true);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server,
                s -> ctx.fail("should not invoke onStartupSuccess"));

        // WHEN starting the adapter
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure());
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has not been invoked
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 403
     * result if the device belongs to a tenant for which the adapter is
     * disabled.
     */
    @Test
    public void testUploadTelemetryFailsForDisabledTenant() {

        // GIVEN an adapter
        final HttpServer server = getHttpServer(false);
        final MessageSender sender = givenATelemetrySenderForOutcome(Future.succeededFuture());

        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapterConfiguration(new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, ADAPTER_TYPE)
                .put(TenantConstants.FIELD_ENABLED, false));
        when(tenantClient.get(eq("my-tenant"), any())).thenReturn(Future.succeededFuture(myTenantConfig));
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device that belongs to "my-tenant" publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final RoutingContext ctx = newRoutingContext(payload);

        adapter.uploadTelemetryMessage(ctx, "my-tenant", "the-device", payload, "application/text");

        // THEN the device gets a 403
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_FORBIDDEN);
        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class));
        // and has not been reported as processed
        verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());
    }

    /**
     * Verifies that the adapter waits for an event being settled and accepted
     * by a downstream peer before responding with a 202 status to the device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadEventWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final RoutingContext ctx = newRoutingContext(payload, response);
        when(ctx.addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadEventMessage(ctx, "tenant", "device", payload, "application/text");

        // THEN the device does not get a response
        verify(response, never()).end();
        // and the message is not reported as being processed
        verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());

        // until the event has been accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(response).setStatusCode(202);
        verify(response).end();
        verify(metrics).incrementProcessedMessages(anyString(), eq("tenant"));
        verify(metrics).incrementProcessedPayload(anyString(), eq("tenant"), eq((long) payload.length()));
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 400
     * result if it is rejected by the downstream peer.
     */
    @Test
    public void testUploadEventFailsForRejectedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes an event that is not accepted by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final RoutingContext ctx = newRoutingContext(payload);

        adapter.uploadEventMessage(ctx, "tenant", "device", payload, "application/text");
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 400
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_BAD_REQUEST);
        // and has not been reported as processed
        verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());
    }

    /**
     * Verifies that the adapter does not wait for a telemetry message being settled and accepted
     * by a downstream peer before responding with a 202 status to the device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadTelemetryDoesNotWaitForAcceptedOutcome() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Future<ProtonDelivery> outcome = Future.succeededFuture(mock(ProtonDelivery.class));
        givenATelemetrySenderForOutcome(outcome);

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final RoutingContext ctx = newRoutingContext(payload, response);
        when(ctx.addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadTelemetryMessage(ctx, "tenant", "device", payload, "application/text");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
        // and the message has been reported as processed
        verify(metrics).incrementProcessedMessages(anyString(), eq("tenant"));
        verify(metrics).incrementProcessedPayload(anyString(), eq("tenant"), eq((long) payload.length()));
    }

    /**
     * Verifies that the adapter uses the max TTD configured for the adapter if a device provides
     * a TTD value that is greater than the max value.
     */
    @Test
    public void testUploadTelemetryUsesConfiguredMaxTtd() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Future<ProtonDelivery> outcome = Future.succeededFuture(mock(ProtonDelivery.class));
        givenATelemetrySenderForOutcome(outcome);

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        final MessageSender sender = givenATelemetrySenderForOutcome(Future.succeededFuture());

        // WHEN a device publishes a telemetry message that belongs to a tenant with
        // a max TTD of 20 secs
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from("tenant", true).setProperty(TenantConstants.FIELD_MAX_TTD, 20));
        });

        // and includes a TTD value of 40 in its request
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TIL_DISCONNECT))).thenReturn("40");
        final RoutingContext ctx = newRoutingContext(payload, request, response);

        adapter.uploadTelemetryMessage(ctx, "tenant", "device", payload, "application/text");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
        // and the downstream message contains the configured max TTD
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), (SpanContext) any());
        assertThat(MessageHelper.getTimeUntilDisconnect(messageCaptor.getValue()), is(20));
    }

    private RoutingContext newRoutingContext(final Buffer payload) {
        return newRoutingContext(payload, mock(HttpServerResponse.class));
    }

    private RoutingContext newRoutingContext(final Buffer payload, final HttpServerResponse response) {
        return newRoutingContext(payload, mock(HttpServerRequest.class), response);
    }

    private RoutingContext newRoutingContext(
            final Buffer payload,
            final HttpServerRequest request,
            final HttpServerResponse response) {

        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.closed()).thenReturn(false);

        final RoutingContext ctx = mock(RoutingContext.class, Mockito.RETURNS_SMART_NULLS);
        when(ctx.getBody()).thenReturn(payload);
        when(ctx.response()).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(ctx.get(TracingHandler.CURRENT_SPAN)).thenReturn(mock(Span.class));
        when(ctx.vertx()).thenReturn(vertx);
        when(ctx.get(Command.KEY_COMMAND)).thenReturn(null);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private HttpServer getHttpServer(final boolean startupShouldFail) {

        final HttpServer server = mock(HttpServer.class);
        when(server.actualPort()).thenReturn(0, 8080);
        when(server.requestHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            final Handler<AsyncResult<HttpServer>> handler = (Handler<AsyncResult<HttpServer>>) invocation
                    .getArgument(0);
            if (startupShouldFail) {
                handler.handle(Future.failedFuture("http server intentionally failed to start"));
            } else {
                handler.handle(Future.succeededFuture(server));
            }
            return server;
        });
        return server;
    }

    /**
     * Creates a protocol adapter for a given HTTP server.
     * 
     * @param server The HTTP server to start.
     * @param onStartupSuccess The handler to invoke on successful startup.
     * @return The adapter.
     */
    private AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> getAdapter(
            final HttpServer server,
            final Handler<Void> onStartupSuccess) {

        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = new AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties>() {

            @Override
            protected String getTypeName() {
                return ADAPTER_TYPE;
            }

            @Override
            protected void addRoutes(final Router router) {
            }

            @Override
            protected void onStartupSuccess() {
                if (onStartupSuccess != null) {
                    onStartupSuccess.handle(null);
                }
            }
        };

        adapter.init(vertx, context);
        adapter.setConfig(config);
        adapter.setMetrics(metrics);
        adapter.setInsecureHttpServer(server);
        adapter.setTenantServiceClient(tenantServiceClient);
        adapter.setHonoMessagingClient(messagingClient);
        adapter.setRegistrationServiceClient(registrationServiceClient);
        adapter.setCredentialsServiceClient(credentialsServiceClient);
        adapter.setCommandConnection(commandConnection);

        return adapter;
    }

    private MessageSender givenAnEventSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.send(any(Message.class), (SpanContext) any())).thenReturn(outcome);

        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private MessageSender givenATelemetrySenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.send(any(Message.class), (SpanContext) any())).thenReturn(outcome);

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private static void assertContextFailedWithClientError(final RoutingContext ctx, final int statusCode) {
        final ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(ctx).fail(exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue(), instanceOf(ClientErrorException.class));
        assertThat(((ClientErrorException) exceptionCaptor.getValue()).getErrorCode(), is(statusCode));
    }
}
