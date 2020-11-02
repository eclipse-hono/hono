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

package org.eclipse.hono.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.service.test.ProtocolAdapterTestSupport;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link AbstractVertxBasedHttpProtocolAdapter}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class AbstractVertxBasedHttpProtocolAdapterTest extends
    ProtocolAdapterTestSupport<HttpProtocolAdapterProperties, AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties>> {

    private static final String ADAPTER_TYPE = "http";
    private static final String CMD_REQ_ID = "12fcmd-client-c925910f-ea2a-455c-a3f9-a339171f335474f48a55-c60d-4b99-8950-a2fbb9e8f1b6";

    private ProtocolAdapterCommandConsumer commandConsumer;
    private Vertx vertx;
    private Context context;
    private HttpAdapterMetrics metrics;
    private ResourceLimitChecks resourceLimitChecks;
    private HttpServer server;
    private Handler<Void> startupHandler;

    /**
     * Sets up common fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        startupHandler = mock(Handler.class);
        context = mock(Context.class);
        vertx = mock(Vertx.class);
        // run timers immediately
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> task = invocation.getArgument(1);
            task.handle(null);
            return 1L;
        });

        metrics = mock(HttpAdapterMetrics.class);

        this.properties = givenDefaultConfigurationProperties();
        createClientFactories();
        createClients();

        commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), any(Handler.class), any(), any()))
            .thenReturn(Future.succeededFuture(commandConsumer));

        resourceLimitChecks = mock(ResourceLimitChecks.class);
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HttpProtocolAdapterProperties givenDefaultConfigurationProperties() {
        properties = new HttpProtocolAdapterProperties();
        properties.setInsecurePortEnabled(true);

        return properties;
    }

    /**
     * Verifies that a client provided HTTP server is started instead of creating and starting a new http server.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartUsesClientProvidedHttpServer(final VertxTestContext ctx) {

        // GIVEN an adapter with a client provided HTTP server
        givenAnAdapter(properties);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(s -> {
            // THEN the client provided HTTP server has been configured and started
            ctx.verify(() -> {
                verify(server).requestHandler(any(Handler.class));
                verify(server).listen(any(Handler.class));
            });
            ctx.completeNow();
        }));
        adapter.start(startupTracker);
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is invoked if the
     * HTTP server has been started successfully.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartInvokesOnStartupSuccess(final VertxTestContext ctx) {

        // GIVEN an adapter with a client provided http server
        givenAnAdapter(properties);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
        startupTracker.future().onComplete(ctx.succeeding(v -> {
            ctx.verify(() -> verify(startupHandler).handle(any()));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if a
     * client provided HTTP server fails to start.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final VertxTestContext ctx) {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        server = getHttpServer(true);
        adapter = getAdapter(server, properties, startupHandler);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has not been invoked
        startupTracker.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> verify(startupHandler, never()).handle(any()));
            ctx.completeNow();
        }));
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
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapter(new Adapter(ADAPTER_TYPE).setEnabled(Boolean.FALSE));
        when(tenantClient.get(eq("my-tenant"), any())).thenReturn(Future.succeededFuture(myTenantConfig));

        // WHEN a device that belongs to "my-tenant" publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext ctx = newHttpContext(payload);

        adapter.uploadTelemetryMessage(ctx, "my-tenant", "the-device", payload, "application/text");

        // THEN the device gets a 403
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_FORBIDDEN);
        // and no Command consumer has been created for the device
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), anyString(), any(Handler.class), any(), any());
        // and the message has not been forwarded downstream
        assertNoTelemetryMessageHasBeenSentDownstream();
        // and has not been reported as processed
        verify(metrics, never())
            .reportTelemetry(
                    any(MetricsTags.EndpointType.class),
                    anyString(),
                    any(),
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
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // with an enabled tenant
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        when(tenantClient.get(eq("my-tenant"), any())).thenReturn(Future.succeededFuture(myTenantConfig));

        // WHEN an unknown device that supposedly belongs to that tenant publishes a telemetry message
        // with a TTD value set
        when(registrationClient.assertRegistration(eq("unknown-device"), any(), any(SpanContext.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TILL_DISCONNECT))).thenReturn("5");
        final HttpContext ctx = newHttpContext(payload, "application/text", request, response);

        adapter.uploadTelemetryMessage(ctx, "my-tenant", "unknown-device", payload, "application/text");

        // THEN the device gets a 404
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_NOT_FOUND);
        verify(registrationClient).assertRegistration(eq("unknown-device"), any(), any(SpanContext.class));
        // and the message has not been forwarded downstream
        assertNoTelemetryMessageHasBeenSentDownstream();
        // and no Command consumer has been created for the device
        verify(commandConsumerFactory, never()).createCommandConsumer(anyString(), anyString(), any(Handler.class), any(), any());
        // and has not been reported as processed
        verify(metrics, never())
            .reportTelemetry(
                    any(MetricsTags.EndpointType.class),
                    anyString(),
                    any(),
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
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpContext ctx = newHttpContext(payload, "application/text", newEventRequest(), response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadEventMessage(ctx, "tenant", "device");
        assertEventHasBeenSentDownstream("tenant", "device", "application/text");
        // THEN the device does not get a response
        verify(response, never()).end();
        // and the message is not reported as being processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any(MetricsTags.TtdStatus.class),
                any());

        // until the event has been accepted
        outcome.complete();
        verify(response).setStatusCode(202);
        verify(response).end();
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.EVENT),
                eq("tenant"),
                any(),
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
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);

        // WHEN a device publishes an event that is not accepted by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext ctx = newHttpContext(payload, newEventRequest(), mock(HttpServerResponse.class));

        adapter.uploadEventMessage(ctx, "tenant", "device", payload, "application/text");
        assertEventHasBeenSentDownstream("tenant", "device", "application/text");
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 400
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_BAD_REQUEST);
        // and has not been reported as processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any(MetricsTags.TtdStatus.class),
                any());
    }

    /**
     * Verifies that the adapter uses the time to live value set in the down stream event message.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadEventWithTimeToLive() {
        // GIVEN an adapter with a downstream event consumer attached
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);

        // WHEN a device publishes an event with a time to live value as a header
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.uri()).thenReturn("/" + EventConstants.EVENT_ENDPOINT);
        when(request.getHeader(eq(Constants.HEADER_TIME_TO_LIVE))).thenReturn("10");
        final HttpContext ctx = newHttpContext(payload, "text/plain", request, response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadEventMessage(ctx, "tenant", "device");

        // verifies that the downstream message contains the time to live value
        assertEventHasBeenSentDownstream("tenant", "device", "text/plain", 10L);
    }

    /**
     * Verifies that the adapter waits for a command response being settled and accepted
     * by a downstream peer before responding with a 202 status to the device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadCommandResponseWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream application attached
        givenAnAdapter(properties);
        final Promise<ProtonDelivery> outcome = Promise.promise();
        givenACommandResponseSenderForAnyTenant(outcome);

        // WHEN a device publishes a command response
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class), response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
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
                any(),
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
                any(),
                eq(ProcessingOutcome.FORWARDED),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter accepts a command response message with an empty body.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadEmptyCommandResponseSucceeds() {

        // GIVEN an adapter with a downstream application attached
        givenACommandResponseSenderForAnyTenant();
        givenAnAdapter(properties);

        // WHEN a device publishes a command response with an empty payload
        final Buffer payload = null;
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class), response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // then it is forwarded successfully
        verify(response).setStatusCode(202);
        verify(response).end();
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.FORWARDED),
                eq(0),
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
        to.addAdapter(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP).setEnabled(Boolean.FALSE));
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(to));

        givenAnAdapter(properties);

        // WHEN a device publishes a command response
        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class), mock(HttpServerResponse.class));

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // THEN the device gets a 403
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_FORBIDDEN);
        // and the response has been reported as undeliverable
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(to),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that an authenticated device that is not a gateway fails to
     * upload a command response for another device.
     */
    @Test
    public void testUploadCommandResponseFailsForOtherDevice() {

        givenAnAdapter(properties);
        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class),
                mock(HttpServerResponse.class));
        final TenantObject to = TenantObject.from("tenant", true);

        // Given an adapter that is enabled for a device's tenant
        to.addAdapter(new Adapter(ADAPTER_TYPE).setEnabled(Boolean.TRUE));
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(to));

        // which is connected to a Credentials service that has credentials on record for device 9999
        when(ctx.getAuthenticatedDevice()).thenReturn(new DeviceUser("tenant", "9999"));

        // but for which no registration information is available
        when(registrationClient.assertRegistration((String) any(), (String) any(), (SpanContext) any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND,
                        "cannot publish data for device of other tenant")));

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // Then the device gets a 404
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_NOT_FOUND);
        // and the response has not been reported as forwarded
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                eq(to),
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
        final Promise<ProtonDelivery> outcome = Promise.promise();
        givenACommandResponseSenderForAnyTenant(outcome);
        givenAnAdapter(properties);

        // WHEN a device publishes a command response that is not accepted by the application
        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class), mock(HttpServerResponse.class));

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 400
        assertContextFailedWithClientError(ctx, HttpURLConnection.HTTP_BAD_REQUEST);
        // and the response has not been reported as forwarded
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
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
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // WHEN a device publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class), response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadTelemetryMessage(ctx, "tenant", "device");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();

        assertTelemetryMessageHasBeenSentDownstream(QoS.AT_MOST_ONCE, "tenant", "device", "application/text");

        // and the message has been reported as processed
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(MetricsTags.TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter closes the command consumer created as part of
     * handling a request with a TTD parameter if sending of the telemetry
     * message fails.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUploadTelemetryWithTtdClosesCommandConsumerIfSendingFails() {

        // GIVEN an adapter
        givenAnAdapter(properties);
        // with the downstream telemetry sender unable to forward messages
        final Promise<Void> sendResult = Promise.promise();
        sendResult.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
        givenATelemetrySenderForAnyTenant(sendResult);

        // WHEN a device publishes a telemetry message with a TTD
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TILL_DISCONNECT))).thenReturn("10");
        final HttpContext ctx = newHttpContext(payload, "text/plain", request, response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });
        // and the creation of the command consumer completes at a later point
        final Promise<ProtocolAdapterCommandConsumer> commandConsumerPromise = Promise.promise();
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), any(Handler.class), any(), any()))
                .thenReturn(commandConsumerPromise.future());

        adapter.uploadTelemetryMessage(ctx, "tenant", "device");
        commandConsumerPromise.complete(commandConsumer);

        // THEN the request fails immediately
        verify(ctx.getRoutingContext()).fail(any());
        // the message has been reported
        verify(metrics).reportTelemetry(
                eq(EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.UNDELIVERABLE),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
        // and the command consumer is closed
        verify(commandConsumer).close(any());
    }

    /**
     * Verifies that the adapter uses the max TTD configured for the adapter if a device provides
     * a TTD value that is greater than the max value.
     */
    @Test
    public void testUploadTelemetryUsesConfiguredMaxTtd() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // WHEN a device publishes a telemetry message that belongs to a tenant with
        // a max TTD of 20 secs
        final TenantObject tenant = TenantObject.from("tenant", true)
                .addAdapter(new Adapter(ADAPTER_TYPE).setEnabled(Boolean.TRUE)
                        .setExtensions(Map.of(TenantConstants.FIELD_MAX_TTD, 20)));
        when(tenantClient.get(eq("tenant"), any())).thenReturn(
                Future.succeededFuture(tenant));

        // and includes a TTD value of 40 in its request
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_TIME_TILL_DISCONNECT))).thenReturn("40");
        final HttpContext ctx = newHttpContext(payload, "text/plain", request, response);

        adapter.uploadTelemetryMessage(ctx, "tenant", "device");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
        // and the downstream message contains the configured max TTD
        verify(telemetrySender).sendTelemetry(
                eq(tenant),
                argThat(assertion -> assertion.getDeviceId().equals("device")),
                eq(org.eclipse.hono.util.QoS.AT_MOST_ONCE),
                eq("text/plain"),
                any(Buffer.class),
                argThat(props -> props.get(MessageHelper.APP_PROPERTY_DEVICE_TTD).equals(20)),
                any());
    }

    /**
     * Verifies that a telemetry message is rejected due to the limit exceeded.
     *
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext routingContext = newHttpContext(payload);

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device that belongs to "my-tenant" publishes a telemetry message
        adapter.uploadTelemetryMessage(routingContext, "my-tenant", "the-device", payload, "application/text");

        // THEN the device gets a 429
        assertContextFailedWithClientError(routingContext, HttpUtils.HTTP_TOO_MANY_REQUESTS);
        assertNoTelemetryMessageHasBeenSentDownstream();
        // the message has been reported
        verify(metrics).reportTelemetry(
                eq(EndpointType.TELEMETRY),
                eq("my-tenant"),
                any(),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that an event message is rejected due to the limit exceeded.
     *
     */
    @Test
    public void testMessageLimitExceededForAnEventMessage() {

        // GIVEN an adapter with a downstream event consumer attached
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        final Buffer payload = Buffer.buffer("some payload");
        final HttpContext routingContext = newHttpContext(payload);

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device that belongs to "my-tenant" publishes an event message
        adapter.uploadEventMessage(routingContext, "my-tenant", "the-device", payload, "application/text");

        // THEN the device gets a 429
        assertContextFailedWithClientError(routingContext, HttpUtils.HTTP_TOO_MANY_REQUESTS);
        assertNoEventHasBeenSentDownstream();
        // the message has been reported
        verify(metrics).reportTelemetry(
                eq(EndpointType.EVENT),
                eq("my-tenant"),
                any(),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that a command response message is rejected due to the limit exceeded.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMessageLimitExceededForACommandResponseMessage() {

        // GIVEN an adapter with a downstream application attached
        givenAnAdapter(properties);
        givenACommandResponseSenderForAnyTenant();

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        // WHEN a device publishes a command response
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final HttpContext ctx = newHttpContext(payload, "application/text", mock(HttpServerRequest.class),
                response);
        when(ctx.getRoutingContext().addBodyEndHandler(any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return 0;
        });

        adapter.uploadCommandResponseMessage(ctx, "tenant", "device", CMD_REQ_ID, 200);

        // THEN the device gets a 429
        assertContextFailedWithClientError(ctx, HttpUtils.HTTP_TOO_MANY_REQUESTS);
        // AND has reported the message as unprocessable
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.UNPROCESSABLE),
                eq(payload.length()),
                any());
    }

    private HttpServerRequest newEventRequest() {
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.uri()).thenReturn("/event");
        return request;
    }

    private HttpContext newHttpContext(final Buffer payload) {
        return newHttpContext(payload, mock(HttpServerResponse.class));
    }

    private HttpContext newHttpContext(final Buffer payload, final HttpServerResponse response) {
        return newHttpContext(payload, mock(HttpServerRequest.class), response);
    }

    private HttpContext newHttpContext(
            final Buffer payload,
            final HttpServerRequest request,
            final HttpServerResponse response) {

        return newHttpContext(payload, null, request, response);
    }

    private HttpContext newHttpContext(
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

        return HttpContext.from(ctx);
    }

    /**
     * Creates a new adapter instance to be tested.
     * <p>
     * This method
     * This method
     * <ol>
     * <li>creates a new {@code HttpServer} by invoking {@link #getHttpServer(boolean)} with {@code false}</li>
     * <li>assigns the result to property <em>server</em></li>
     * <li>creates a new adapter by invoking {@link #getAdapter(HttpServer, HttpProtocolAdapterProperties, Handler)}
     * with the server, configuration and the startupHandler</li>
     * <li>assigns the result to property <em>adapter</em></li>
     * </ol>
     *
     * @param configuration The configuration properties to use.
     * @return The adapter instance.
     */
    private AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> givenAnAdapter(
            final HttpProtocolAdapterProperties configuration) {
        this.server = getHttpServer(false);
        this.adapter = getAdapter(this.server, configuration, startupHandler);
        return adapter;
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
     * @param configuration The configuration properties to use.
     * @param onStartupSuccess The handler to invoke on successful startup.
     * @return The adapter.
     */
    private AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> getAdapter(
            final HttpServer server,
            final HttpProtocolAdapterProperties configuration,
            final Handler<Void> onStartupSuccess) {

        final AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = new AbstractVertxBasedHttpProtocolAdapter<>() {

            @Override
            protected String getTypeName() {
                return ADAPTER_TYPE;
            }

            @Override
            protected void addRoutes(final Router router) {
            }

            @Override
            protected void onStartupSuccess() {
                Optional.ofNullable(onStartupSuccess).ifPresent(h -> h.handle(null));
            }
        };

        adapter.init(vertx, context);
        adapter.setConfig(configuration);
        adapter.setMetrics(metrics);
        adapter.setInsecureHttpServer(server);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        setServiceClients(adapter);
        return adapter;
    }

    private static void assertContextFailedWithClientError(final HttpContext ctx, final int statusCode) {
        final ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(ctx.getRoutingContext()).fail(exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue()).isInstanceOf(ClientErrorException.class);
        assertThat(((ClientErrorException) exceptionCaptor.getValue()).getErrorCode()).isEqualTo(statusCode);
    }
}
