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

package org.eclipse.hono.adapter.http;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandConnection;
import org.eclipse.hono.client.CommandConsumer;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
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
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.ParsedHeaderValues;
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
    private static final String CMD_REQ_ID = "12fcmd-client-c925910f-ea2a-455c-a3f9-a339171f335474f48a55-c60d-4b99-8950-a2fbb9e8f1b6";

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

        commandConsumer = mock(CommandConsumer.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> resultHandler = invocation.getArgument(0);
            if (resultHandler != null) {
                resultHandler.handle(Future.succeededFuture());
            }
            return null;
        }).when(commandConsumer).close(any(Handler.class));
        commandConnection = mock(CommandConnection.class);
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));
        when(commandConnection.createCommandConsumer(anyString(), anyString(), any(Handler.class), any(Handler.class)))
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
     * Verifies that the adapter fails the upload of a message with a 403
     * result if the device belongs to a tenant for which the adapter is
     * disabled.
     */
    @SuppressWarnings("unchecked")
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
        // and no Command consumer has been created for the device
        verify(commandConnection, never()).createCommandConsumer(anyString(), anyString(), any(Handler.class), any(Handler.class));
        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class));
        // and has not been reported as processed
        verify(metrics, never())
            .reportTelemetry(
                    any(MetricsTags.EndpointType.class),
                    anyString(),
                    eq(MetricsTags.ProcessingOutcome.FORWARDED),
                    any(MetricsTags.QoS.class),
                    anyInt(),
                    any(MetricsTags.TtdStatus.class),
                    any());
    }

    /**
     * Verifies that the adapter fails the upload of a message containing
     * a TTD value with a 404 result if the device is not registered.
     * Also verifies that the adapter does not open a command consumer for
     * the device in this case.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadTelemetryFailsForUnknownDevice() {

        // GIVEN an adapter
        final HttpServer server = getHttpServer(false);
        final MessageSender sender = givenATelemetrySenderForOutcome(Future.succeededFuture());

        // with an enabled tenant
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        when(tenantClient.get(eq("my-tenant"), any())).thenReturn(Future.succeededFuture(myTenantConfig));
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN an unknown device that supposedly belongs to that tenant publishes a telemetry message
        // with a TTD value set
        when(regClient.assertRegistration(eq("unknown-device"), any(), any(SpanContext.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TIL_DISCONNECT))).thenReturn("5");
        final RoutingContext ctx = newRoutingContext(payload, "application/text", request, response);

        adapter.uploadTelemetryMessage(ctx, "my-tenant", "unknown-device", payload, "application/text");

        // THEN the device gets a 404
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_NOT_FOUND);
        verify(regClient).assertRegistration(eq("unknown-device"), any(), any(SpanContext.class));
        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class), any(SpanContext.class));
        // and no Command consumer has been created for the device
        verify(commandConnection, never()).createCommandConsumer(anyString(), anyString(), any(Handler.class), any(Handler.class));
        // and has not been reported as processed
        verify(metrics, never())
            .reportTelemetry(
                    any(MetricsTags.EndpointType.class),
                    anyString(),
                    eq(MetricsTags.ProcessingOutcome.FORWARDED),
                    any(MetricsTags.QoS.class),
                    anyInt(),
                    any(MetricsTags.TtdStatus.class),
                    any());
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
        final RoutingContext ctx = newRoutingContext(payload, "application/text", mock(HttpServerRequest.class), response);
        when(ctx.addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadEventMessage(ctx, "tenant", "device");

        // THEN the device does not get a response
        verify(response, never()).end();
        // and the message is not reported as being processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any(MetricsTags.TtdStatus.class),
                any());

        // until the event has been accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(response).setStatusCode(202);
        verify(response).end();
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.EVENT),
                eq("tenant"),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(MetricsTags.TtdStatus.NONE),
                any());
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
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any(MetricsTags.TtdStatus.class),
                any());
    }

    /**
     * Verifies that the adapter waits for a command response being settled and accepted
     * by a downstream peer before responding with a 202 status to the device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadCommandResponseWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream application attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenACommandResponseSenderForOutcome(outcome);

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes a command response
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final RoutingContext ctx = newRoutingContext(payload, "application/text", mock(HttpServerRequest.class), response);
        when(ctx.addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // THEN the device does not get a response
        verify(response, never()).end();
        // and the response has not been reported as forwarded
        verify(metrics, never()).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(ProcessingOutcome.FORWARDED),
                anyInt(),
                any());

        // until the command response has been accepted by the application
        outcome.complete(mock(ProtonDelivery.class));
        verify(response).setStatusCode(202);
        verify(response).end();
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(ProcessingOutcome.FORWARDED),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 403
     * if the adapter is disabled for the device's tenant.
     */
    @Test
    public void testUploadCommandResponseFailsForDisabledTenant() {

        // GIVEN an adapter that is not enabled for a device's tenant
        final TenantObject to = TenantObject.from("tenant", true);
        to.addAdapterConfiguration(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_HTTP, false));
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(to));

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes a command response
        final Buffer payload = Buffer.buffer("some payload");
        final RoutingContext ctx = newRoutingContext(payload, "application/text", mock(HttpServerRequest.class), mock(HttpServerResponse.class));

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // THEN the device gets a 403
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_FORBIDDEN);
        // and the response has been reported as undeliverable
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 404 if the device is not registered and
     * the valid credentials for the device is available.
     */
    @Test
    public void testUploadCommandResponseFailsForNonExistingDevice() {

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        final Buffer payload = Buffer.buffer("some payload");
        final RoutingContext ctx = newRoutingContext(payload, "application/text", mock(HttpServerRequest.class),
                mock(HttpServerResponse.class));
        final TenantObject to = TenantObject.from("tenant", true);

        // Given an adapter that is enabled for a device's tenant
        to.addAdapterConfiguration(new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, ADAPTER_TYPE)
                .put(TenantConstants.FIELD_ENABLED, true));
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(to));

        // which is connected to a Credentials service that has credentials on record for device 9999
        when(adapter.getAuthenticatedDevice(ctx)).thenReturn(new DeviceUser("tenant", "9999"));

        // but for which no registration information is available
        when(regClient.assertRegistration((String) any(), (String) any(), (SpanContext) any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                        "cannot publish data for device of other tenant")));

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // Then the device gets a 404
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_NOT_FOUND);
        // and the response has not been reported as forwarded
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 400
     * result if it is rejected by the downstream peer.
     */
    @Test
    public void testUploadCommandResponseFailsForRejectedOutcome() {

        // GIVEN an adapter with a downstream application attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenACommandResponseSenderForOutcome(outcome);

        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes a command response that is not accepted by the application
        final Buffer payload = Buffer.buffer("some payload");
        final RoutingContext ctx = newRoutingContext(payload, "application/text", mock(HttpServerRequest.class), mock(HttpServerResponse.class));

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 400
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_BAD_REQUEST);
        // and the response has not been reported as forwarded
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(payload.length()),
                any());
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
        final RoutingContext ctx = newRoutingContext(payload, "application/text", mock(HttpServerRequest.class), response);
        when(ctx.addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadTelemetryMessage(ctx, "tenant", "device");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
        // and the message has been reported as processed
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(MetricsTags.TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter does not include a TTD value provided by a device
     * in the downstream message if the Command consumer for the device is already
     * in use.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadTelemetryRemovesTtdIfCommandConsumerIsInUse() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        final MessageSender sender = givenATelemetrySenderForOutcome(Future.succeededFuture());

        // WHEN a device publishes a telemetry message with a TTD
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TIL_DISCONNECT))).thenReturn("10");
        final RoutingContext ctx = newRoutingContext(payload, "text/plain", request, response);
        when(ctx.addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });
        // and the Command consumer for the device is already in use
        when(commandConnection.createCommandConsumer(eq("tenant"), eq("device"), any(Handler.class), any()))
            .thenReturn(Future.failedFuture(new ResourceConflictException("consumer in use")));
        adapter.uploadTelemetryMessage(ctx, "tenant", "device");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
        // the message has been reported
        verify(metrics).reportTelemetry(
                eq(EndpointType.TELEMETRY),
                eq("tenant"),
                eq(ProcessingOutcome.FORWARDED),
                eq(QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
        // and the downstream message does not contain any TTD value
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), (SpanContext) any());
        assertNull(MessageHelper.getTimeUntilDisconnect(messageCaptor.getValue()));
    }

    /**
     * Verifies that the adapter does not forward an empty notification if the Command consumer
     * for the device is already in use.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadEmptyNotificationSucceedsIfCommandConsumerIsInUse() {

        // GIVEN an adapter with a downstream event consumer attached
        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        final MessageSender sender = givenAnEventSenderForOutcome(Future.succeededFuture());

        // WHEN a device publishes an empty notification event with a TTD
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TIL_DISCONNECT))).thenReturn("10");
        final RoutingContext ctx = newRoutingContext(null, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION, request, response);
        // and the Command consumer for the device is already in use
        when(commandConnection.createCommandConsumer(eq("tenant"), eq("device"), any(Handler.class), any()))
            .thenReturn(Future.failedFuture(new ResourceConflictException("consumer in use")));
        adapter.uploadEventMessage(ctx, "tenant", "device");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
        // and no event is being sent downstream
        verify(sender, never()).sendAndWaitForOutcome(any(Message.class), (SpanContext) any());
        verify(metrics, never()).reportTelemetry(
                eq(EndpointType.EVENT),
                anyString(),
                any(ProcessingOutcome.class),
                eq(QoS.AT_LEAST_ONCE),
                anyInt(),
                any(TtdStatus.class),
                any());
    }

    /**
     * Verifies that the adapter uses the max TTD configured for the adapter if a device provides
     * a TTD value that is greater than the max value.
     */
    @Test
    public void testUploadTelemetryUsesConfiguredMaxTtd() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final HttpServer server = getHttpServer(false);
        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        final MessageSender sender = givenATelemetrySenderForOutcome(Future.succeededFuture());

        // WHEN a device publishes a telemetry message that belongs to a tenant with
        // a max TTD of 20 secs
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenReturn(
            Future.succeededFuture(TenantObject.from("tenant", true).setProperty(TenantConstants.FIELD_MAX_TTD, 20)));

        // and includes a TTD value of 40 in its request
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TIL_DISCONNECT))).thenReturn("40");
        final RoutingContext ctx = newRoutingContext(payload, "application/text", request, response);

        adapter.uploadTelemetryMessage(ctx, "tenant", "device");

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

        return newRoutingContext(payload, null, request, response);
    }

    private RoutingContext newRoutingContext(
            final Buffer payload,
            final String contentType,
            final HttpServerRequest request,
            final HttpServerResponse response) {

        when(response.setStatusCode(anyInt())).thenReturn(response);
        when(response.closed()).thenReturn(false);


        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.getBody()).thenReturn(payload);
        when(ctx.response()).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(ctx.get(TracingHandler.CURRENT_SPAN)).thenReturn(mock(Span.class));
        when(ctx.vertx()).thenReturn(vertx);
        when(ctx.get(CommandContext.KEY_COMMAND_CONTEXT)).thenReturn(null);
        when(ctx.get(MetricsTags.TtdStatus.class.getName())).thenReturn(MetricsTags.TtdStatus.NONE);

        if (contentType != null) {
            final MIMEHeader headerContentType = mock(MIMEHeader.class);
            when(headerContentType.value()).thenReturn(contentType);
            final ParsedHeaderValues headers = mock(ParsedHeaderValues.class);
            when(headers.contentType()).thenReturn(headerContentType);
            when(ctx.parsedHeaders()).thenReturn(headers);
        }
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

    private CommandResponseSender givenACommandResponseSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final CommandResponseSender sender = mock(CommandResponseSender.class);
        when(sender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(outcome);

        when(commandConnection.getCommandResponseSender(anyString(), anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private MessageSender givenAnEventSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(outcome);

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
