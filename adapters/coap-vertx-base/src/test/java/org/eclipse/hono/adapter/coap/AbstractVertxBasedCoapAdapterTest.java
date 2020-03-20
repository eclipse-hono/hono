/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.coap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link AbstractVertxBasedCoapAdapter}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class AbstractVertxBasedCoapAdapterTest {

    private static final String ADAPTER_TYPE = "coap";

    private static final Vertx vertx = Vertx.vertx();

    private CredentialsClientFactory credentialsClientFactory;
    private TenantClientFactory tenantClientFactory;
    private DownstreamSenderFactory downstreamSenderFactory;
    private RegistrationClientFactory registrationClientFactory;
    private RegistrationClient regClient;
    private TenantClient tenantClient;
    private CoapAdapterProperties config;
    private CommandConsumerFactory commandConsumerFactory;
    private MessageConsumer commandConsumer;
    private DeviceConnectionClientFactory deviceConnectionClientFactory;
    private ResourceLimitChecks resourceLimitChecks;
    private CoapAdapterMetrics metrics;

    /**
     * Sets up common fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {

        config = new CoapAdapterProperties();
        config.setInsecurePortEnabled(true);
        config.setAuthenticationRequired(false);

        metrics = mock(CoapAdapterMetrics.class);

        regClient = mock(RegistrationClient.class);
        when(regClient.assertRegistration(anyString(), any(), any(SpanContext.class))).thenReturn(Future.succeededFuture(new JsonObject()));

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), any(SpanContext.class))).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });

        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsClientFactory = mock(CredentialsClientFactory.class);
        when(credentialsClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        downstreamSenderFactory = mock(DownstreamSenderFactory.class);
        when(downstreamSenderFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        registrationClientFactory = mock(RegistrationClientFactory.class);
        when(registrationClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(registrationClientFactory.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));

        commandConsumer = mock(MessageConsumer.class);
        commandConsumerFactory = mock(CommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), any(Handler.class), any(Handler.class)))
            .thenReturn(Future.succeededFuture(commandConsumer));
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), anyString(), any(Handler.class), any(Handler.class)))
            .thenReturn(Future.succeededFuture(commandConsumer));

        deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        resourceLimitChecks = mock(ResourceLimitChecks.class);
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
    }

    /**
     * Cleans up fixture.
     */
    @AfterAll
    public static void shutDown() {
        vertx.close();
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is invoked if the coap server has been started successfully.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartInvokesOnStartupSuccess(final VertxTestContext ctx) {

        // GIVEN an adapter
        final CoapServer server = getCoapServer(false);
        final Checkpoint onStartupSuccess = ctx.checkpoint();

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true,
                s -> onStartupSuccess.flag());

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().setHandler(ctx.completing());
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
    }

    /**
     * Verifies that the adapter registers resources as part of the start-up process.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartRegistersResources(final VertxTestContext ctx) {

        // GIVEN an adapter
        final CoapServer server = getCoapServer(false);
        // and a set of resources
        final Resource resource = mock(Resource.class);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, s -> {});
        adapter.setResources(Collections.singleton(resource));

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().setHandler(ctx.succeeding(s -> {
            // THEN the resources have been registered with the server
            final ArgumentCaptor<VertxCoapResource> resourceCaptor = ArgumentCaptor.forClass(VertxCoapResource.class);
            ctx.verify(() -> {
                verify(server).add(resourceCaptor.capture());
                assertThat(resourceCaptor.getValue().getWrappedResource()).isEqualTo(resource);
            });
            ctx.completeNow();
        }));
        adapter.start(startupTracker);

    }

    /**
     * Verifies that the resources registered with the adapter are always
     * executed on the adapter's vert.x context.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testResourcesAreRunOnVertxContext(final VertxTestContext ctx) {

        // GIVEN an adapter
        final Context context = vertx.getOrCreateContext();
        final CoapServer server = getCoapServer(false);
        // with a resource
        final Promise<Void> resourceInvocation = Promise.promise();
        final Resource resource = new CoapResource("test") {

            @Override
            public void handleGET(final CoapExchange exchange) {
                ctx.verify(() -> assertThat(Vertx.currentContext()).isEqualTo(context));
                resourceInvocation.complete();
            }
        };

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, s -> {});
        adapter.setResources(Collections.singleton(resource));

        final Promise<Void> startupTracker = Promise.promise();
        adapter.init(vertx, context);
        adapter.start(startupTracker);

        startupTracker.future()
            .compose(ok -> {
                // WHEN the resource receives a GET request
                final Request request = new Request(Code.GET);
                final Exchange getExchange = new Exchange(request, Origin.REMOTE, mock(Executor.class));
                final ArgumentCaptor<VertxCoapResource> resourceCaptor = ArgumentCaptor.forClass(VertxCoapResource.class);
                verify(server).add(resourceCaptor.capture());
                resourceCaptor.getValue().handleRequest(getExchange);
                // THEN the resource's handler has been run on the adapter's vert.x event loop
                return resourceInvocation.future();
            })
            .setHandler(ctx.completing());
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if no credentials authentication provider is
     * set.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartUpFailsIfCredentialsClientFactoryIsNotSet(final VertxTestContext ctx) {

        // GIVEN an adapter that has not all required service clients set
        final CoapServer server = getCoapServer(false);
        @SuppressWarnings("unchecked")
        final Handler<Void> successHandler = mock(Handler.class);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, false, successHandler);

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);

        // THEN startup has failed
        startupTracker.future().setHandler(ctx.failing(t -> {
            // and the onStartupSuccess method has not been invoked
            ctx.verify(() -> verify(successHandler, never()).handle(any()));
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if a client provided coap server fails to
     * start.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final VertxTestContext ctx) {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        final CoapServer server = getCoapServer(true);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true,
                s -> ctx.failNow(new AssertionError("should not have invoked onStartupSuccess")));

        // WHEN starting the adapter
        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);
        // THEN the onStartupSuccess method has not been invoked, see ctx.fail
        startupTracker.future().setHandler(ctx.failing(s -> {
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.03 result if the device belongs to a tenant for
     * which the adapter is disabled.
     */
    @Test
    public void testUploadTelemetryFailsForDisabledTenant() {

        // GIVEN an adapter
        final DownstreamSender sender = givenATelemetrySender(Promise.promise());
        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapter(new Adapter(ADAPTER_TYPE).setEnabled(Boolean.FALSE));
        when(tenantClient.get(eq("my-tenant"), any(SpanContext.class))).thenReturn(Future.succeededFuture(myTenantConfig));
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device that belongs to "my-tenant" publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, MediaTypeRegistry.TEXT_PLAIN);
        final Device authenticatedDevice = new Device("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the device gets a response with code 4.03
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.FORBIDDEN.equals(res.getCode())));

        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class), any(SpanContext.class));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("my-tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.00 result
     * if the request body is not empty but doesn't contain a content-format option.
     */
    @Test
    public void testUploadTelemetryFailsForMissingContentFormat() {

        // GIVEN an adapter
        final DownstreamSender sender = givenATelemetrySender(Promise.promise());
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes a non-empty message that lacks a content-format option
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, (Integer) null);
        final Device authenticatedDevice = new Device("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the device gets a response with code 4.00
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.BAD_REQUEST.equals(res.getCode())));

        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class), any(SpanContext.class));
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                any(MetricsTags.ProcessingOutcome.class),
                any(MetricsTags.QoS.class),
                anyInt(),
                any(TtdStatus.class),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.00 result
     * if the request body is empty but is not marked as an empty notification.
     */
    @Test
    public void testUploadTelemetryFailsForEmptyBody() {

        // GIVEN an adapter
        final DownstreamSender sender = givenATelemetrySender(Promise.promise());
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an empty message that doesn't contain
        // a URI-query option
        final CoapExchange coapExchange = newCoapExchange(null, Type.NON, MediaTypeRegistry.UNDEFINED);
        final Device authenticatedDevice = new Device("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the device gets a response with code 4.00
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.BAD_REQUEST.equals(res.getCode())));

        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class), any(SpanContext.class));
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                any(MetricsTags.ProcessingOutcome.class),
                any(MetricsTags.QoS.class),
                anyInt(),
                any(TtdStatus.class),
                any());
    }

    /**
     * Verifies that the adapter forwards an empty notification downstream.
     */
    @Test
    public void testUploadEmptyNotificationSucceeds() {

        // GIVEN an adapter
        final DownstreamSender sender = givenATelemetrySender(Promise.promise());
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an empty message that is marked as an empty notification
        final OptionSet options = new OptionSet();
        options.addUriQuery(CoapContext.PARAM_EMPTY_CONTENT);
        final CoapExchange coapExchange = newCoapExchange(null, Type.NON, options);
        final Device authenticatedDevice = new Device("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the device gets a response indicating success
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        // and the message has been forwarded downstream
        verify(sender).send(argThat(msg -> EventConstants.isEmptyNotificationType(msg.getContentType())), any(SpanContext.class));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("my-tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(0),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter immediately responds with a 2.04 status if a
     * device publishes telemetry data using a NON message.
     */
    @Test
    public void testUploadTelemetryWithQoS0() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final DownstreamSender sender = givenATelemetrySender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, MediaTypeRegistry.TEXT_PLAIN);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the device gets a response indicating success
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        // and the message has been forwarded downstream
        verify(sender).send(any(Message.class), any(SpanContext.class));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter waits for a the AMQP Messaging Network to accept a
     * forwarded telemetry message that has been published using a CON message,
     * before responding with a 2.04 status to the device.
     */
    @Test
    public void testUploadTelemetryWithQoS1() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final DownstreamSender sender = givenATelemetrySender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an telemetry message with QoS 1
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final CoapContext context = CoapContext.fromRequest(coapExchange);
        final Device authenticatedDevice = new Device("tenant", "device");

        adapter.uploadTelemetryMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the message is being forwarded downstream
        verify(sender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class));
        // and the device does not get a response
        verify(coapExchange, never()).respond(any(Response.class));
        // until the telemetry message has been accepted
        outcome.complete(mock(ProtonDelivery.class));

        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter waits for an event being send with wait for outcome before responding with a 2.04
     * status to the device.
     */
    @Test
    public void testUploadEventWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final DownstreamSender sender = givenAnEventSender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadEventMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the message is being forwarded downstream
        verify(sender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class));
        // but the device does not get a response
        verify(coapExchange, never()).respond(any(Response.class));

        // until the event has been accepted
        outcome.complete(mock(ProtonDelivery.class));

        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.EVENT),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.00 result if it is rejected by the downstream
     * peer.
     */
    @Test
    public void testUploadEventFailsForRejectedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final DownstreamSender sender = givenAnEventSender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an event that is not accepted by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);

        adapter.uploadEventMessage(ctx, authenticatedDevice, authenticatedDevice);
        verify(sender).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class));
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 4.00
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.BAD_REQUEST.equals(res.getCode())));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.EVENT),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter waits for a command response being settled and accepted
     * by a downstream peer before responding with a 2.04 status to the device.
     */
    @Test
    public void testUploadCommandResponseWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream application attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final CommandResponseSender sender = givenACommandResponseSender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an command response
        final String reqId = Command.getRequestId("correlation", "replyToId", "device");
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadCommandResponseMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the command response is being forwarded downstream
        verify(sender).sendCommandResponse(any(CommandResponse.class), any(SpanContext.class));
        // but the device does not get a response
        verify(coapExchange, never()).respond(any(Response.class));
        // and the response has not been reported as forwarded
        verify(metrics, never()).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.FORWARDED),
                anyInt(),
                any());

        // until the message has been accepted
        outcome.complete(mock(ProtonDelivery.class));

        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        verify(metrics).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.FORWARDED),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 4.00
     * response code if it is rejected by the downstream peer.
     */
    @Test
    public void testUploadCommandResponseFailsForRejectedOutcome() {

        // GIVEN an adapter with a downstream application attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final CommandResponseSender sender = givenACommandResponseSender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an command response
        final String reqId = Command.getRequestId("correlation", "replyToId", "device");
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadCommandResponseMessage(context, authenticatedDevice, authenticatedDevice);
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the command response is being forwarded downstream
        verify(sender).sendCommandResponse(any(CommandResponse.class), any(SpanContext.class));
        // and the device gets a 4.00 response
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.BAD_REQUEST.equals(res.getCode())));
        // and the response has not been reported as forwarded
        verify(metrics, never()).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.FORWARDED),
                anyInt(),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 4.03
     * if the adapter is disabled for the device's tenant.
     */
    @Test
    public void testUploadCommandResponseFailsForDisabledTenant() {

        // GIVEN an adapter that is not enabled for a device's tenant
        final TenantObject to = TenantObject.from("tenant", true);
        to.addAdapter(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP).setEnabled(Boolean.FALSE));
        when(tenantClient.get(eq("tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(to));

        final Promise<ProtonDelivery> outcome = Promise.promise();
        final CommandResponseSender sender = givenACommandResponseSender(outcome);
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an command response
        final String reqId = Command.getRequestId("correlation", "replyToId", "device");
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange);

        adapter.uploadCommandResponseMessage(context, authenticatedDevice, authenticatedDevice);

        // THEN the command response not been forwarded downstream
        verify(sender, never()).sendCommandResponse(any(CommandResponse.class), any(SpanContext.class));
        // and the device gets a 4.03 response
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.FORBIDDEN.equals(res.getCode())));
        // and the response has not been reported as forwarded
        verify(metrics, never()).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.FORWARDED),
                anyInt(),
                any());
    }

    /**
     * Verifies that a telemetry message is rejected due to the limit exceeded.
     *
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final DownstreamSender sender = givenATelemetrySender(outcome);

        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device that belongs to a tenant for which the message limit is exceeded
        // publishes a telemetry message
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
            .thenReturn(Future.succeededFuture(Boolean.TRUE));
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, MediaTypeRegistry.TEXT_PLAIN);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);
        adapter.uploadTelemetryMessage(ctx, authenticatedDevice, authenticatedDevice);

        // THEN the message is not being forwarded downstream
        verify(sender, never()).send(any(Message.class), any(SpanContext.class));
        // and the device gets a 4.29
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.TOO_MANY_REQUESTS.equals(res.getCode())));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
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
        final Promise<ProtonDelivery> outcome = Promise.promise();
        final DownstreamSender sender = givenAnEventSender(outcome);

        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        // WHEN a device publishes an event message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);
        adapter.uploadEventMessage(ctx, authenticatedDevice, authenticatedDevice);

        // THEN the message is not being forwarded downstream
        verify(sender, never()).sendAndWaitForOutcome(any(Message.class), any(SpanContext.class));
        // and the device gets a 4.29
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(coapExchange).respond(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(ResponseCode.TOO_MANY_REQUESTS);
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.EVENT),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    private static CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final Integer contentFormat) {

        final OptionSet options = new OptionSet();
        Optional.ofNullable(contentFormat).ifPresent(options::setContentFormat);
        return newCoapExchange(payload, requestType, options);
    }

    private static CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final OptionSet options) {

        final Request request = mock(Request.class);
        when(request.getType()).thenReturn(requestType);
        when(request.isConfirmable()).thenReturn(requestType == Type.CON);
        when(request.getOptions()).thenReturn(options);
        final Exchange echange = new Exchange(request, Origin.REMOTE, mock(Executor.class));
        final CoapExchange coapExchange = mock(CoapExchange.class);
        when(coapExchange.advanced()).thenReturn(echange);
        Optional.ofNullable(payload).ifPresent(b -> when(coapExchange.getRequestPayload()).thenReturn(b.getBytes()));
        when(coapExchange.getRequestOptions()).thenReturn(options);
        when(coapExchange.getQueryParameter(anyString())).thenAnswer(invocation -> {
            final String key = invocation.getArgument(0);
            return options.getUriQuery().stream()
                    .map(param -> param.split("=", 2))
                    .filter(keyValue -> key.equals(keyValue[0]))
                    .findFirst()
                    .map(keyValue -> keyValue.length < 2 ? Boolean.TRUE.toString() : keyValue[1])
                    .orElse(null);
        });
        return coapExchange;
    }

    private CoapServer getCoapServer(final boolean startupShouldFail) {

        final CoapServer server = mock(CoapServer.class);
        if (startupShouldFail) {
            doThrow(new IllegalStateException("Coap Server start with intended failure!")).when(server).start();
        } else {
            doNothing().when(server).start();
        }
        return server;
    }

    /**
     * Creates a protocol adapter for a given HTTP server.
     * 
     * @param server The coap server.
     * @param complete {@code true}, if that adapter should be created with all Hono service clients set, {@code false}, if the
     *            adapter should be created, and all Hono service clients set, but the credentials client is not set.
     * @param onStartupSuccess The handler to invoke on successful startup.
     * 
     * @return The adapter.
     */
    private AbstractVertxBasedCoapAdapter<CoapAdapterProperties> getAdapter(
            final CoapServer server,
            final boolean complete,
            final Handler<Void> onStartupSuccess) {

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = new AbstractVertxBasedCoapAdapter<>() {

            @Override
            protected String getTypeName() {
                return ADAPTER_TYPE;
            }

            @Override
            protected void onStartupSuccess() {
                if (onStartupSuccess != null) {
                    onStartupSuccess.handle(null);
                }
            }
        };

        adapter.setConfig(config);
        adapter.setCoapServer(server);
        adapter.setTenantClientFactory(tenantClientFactory);
        adapter.setDownstreamSenderFactory(downstreamSenderFactory);
        adapter.setRegistrationClientFactory(registrationClientFactory);
        if (complete) {
            adapter.setCredentialsClientFactory(credentialsClientFactory);
        }
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setDeviceConnectionClientFactory(deviceConnectionClientFactory);
        adapter.setMetrics(metrics);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.init(vertx, mock(Context.class));

        return adapter;
    }

    private CommandResponseSender givenACommandResponseSender(final Promise<ProtonDelivery> outcome) {

        final CommandResponseSender sender = mock(CommandResponseSender.class);
        when(sender.sendCommandResponse(any(CommandResponse.class), (SpanContext) any())).thenReturn(outcome.future());

        when(commandConsumerFactory.getCommandResponseSender(anyString(), anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private DownstreamSender givenAnEventSender(final Promise<ProtonDelivery> outcome) {

        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class), any(SpanContext.class))).thenReturn(outcome.future());

        when(downstreamSenderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private DownstreamSender givenATelemetrySender(final Promise<ProtonDelivery> outcome) {

        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.send(any(Message.class), any(SpanContext.class))).thenAnswer(invocation -> {
            outcome.complete(mock(ProtonDelivery.class));
            return outcome.future();
        });
        when(sender.sendAndWaitForOutcome(any(Message.class), any(SpanContext.class))).thenReturn(outcome.future());

        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }
}
