/**
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.concurrent.Executor;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
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
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.service.plan.ResourceLimitChecks;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link AbstractVertxBasedCoapAdapter}.
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedCoapAdapterTest {

    private static final String ADAPTER_TYPE = "coap";

    private static final Vertx vertx = Vertx.vertx();

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public final Timeout globalTimeout = Timeout.seconds(5);

    private CredentialsClientFactory credentialsClientFactory;
    private TenantClientFactory tenantClientFactory;
    private DownstreamSenderFactory downstreamSenderFactory;
    private RegistrationClientFactory registrationClientFactory;
    private RegistrationClient regClient;
    private TenantClient tenantClient;
    private CoapAdapterProperties config;
    private CommandConsumerFactory commandConsumerFactory;
    private DeviceConnectionClientFactory deviceConnectionClientFactory;
    private ResourceLimitChecks resourceLimitChecks;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {

        config = new CoapAdapterProperties();
        config.setInsecurePortEnabled(true);
        config.setAuthenticationRequired(false);

        regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject();
        when(regClient.assertRegistration(anyString(), any(), any())).thenReturn(Future.succeededFuture(result));

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), any())).thenAnswer(invocation -> {
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

        commandConsumerFactory = mock(CommandConsumerFactory.class);
        when(commandConsumerFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        deviceConnectionClientFactory = mock(DeviceConnectionClientFactory.class);
        when(deviceConnectionClientFactory.connect()).thenReturn(Future.succeededFuture(mock(HonoConnection.class)));

        resourceLimitChecks = mock(ResourceLimitChecks.class);
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong()))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
    }

    /**
     * Cleans up fixture.
     */
    @AfterClass
    public static void shutDown() {
        vertx.close();
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is invoked if the coap server has been started successfully.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartInvokesOnStartupSuccess(final TestContext ctx) {

        // GIVEN an adapter
        final CoapServer server = getCoapServer(false);
        final Async onStartupSuccess = ctx.async();

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true,
                s -> onStartupSuccess.complete());
        adapter.setMetrics(mock(CoapAdapterMetrics.class));

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
     * Verifies that the adapter registers resources as part of the start-up process.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartRegistersResources(final TestContext ctx) {

        // GIVEN an adapter
        final CoapServer server = getCoapServer(false);
        // and a set of resources
        final Resource resource = mock(Resource.class);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, s -> {});
        adapter.setMetrics(mock(CoapAdapterMetrics.class));
        adapter.setResources(Collections.singleton(resource));

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);
        startup.await();

        // THEN the resources have been registered with the server
        final ArgumentCaptor<VertxCoapResource> resourceCaptor = ArgumentCaptor.forClass(VertxCoapResource.class);
        verify(server).add(resourceCaptor.capture());
        ctx.assertEquals(resource, resourceCaptor.getValue().getWrappedResource());
    }

    /**
     * Verifies that the resources registered with the adapter are always
     * executed on the adapter's vert.x context.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testResourcesAreRunOnVertxContext(final TestContext ctx) {

        // GIVEN an adapter
        final Context context = vertx.getOrCreateContext();
        final CoapServer server = getCoapServer(false);
        // with a resource
        final Async resourceInvocation = ctx.async();
        final Resource resource = new CoapResource("test") {

            @Override
            public void handleGET(final CoapExchange exchange) {
                ctx.assertEquals(context, Vertx.currentContext());
                resourceInvocation.complete();
            }
        };

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, s -> {});
        adapter.setMetrics(mock(CoapAdapterMetrics.class));
        adapter.setResources(Collections.singleton(resource));

        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.init(vertx, context);
        adapter.start(startupTracker);
        startup.await();

        // WHEN the resource receives a GET request
        final Request request = new Request(Code.GET);
        final Exchange getExchange = new Exchange(request, Origin.REMOTE, mock(Executor.class));
        final ArgumentCaptor<VertxCoapResource> resourceCaptor = ArgumentCaptor.forClass(VertxCoapResource.class);
        verify(server).add(resourceCaptor.capture());
        resourceCaptor.getValue().handleRequest(getExchange);
        // THEN the resource's handler has been run on the adapter's vert.x event loop
        resourceInvocation.await();
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if no credentials authentication provider is
     * set.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartUpFailsIfCredentialsAuthProviderIsNotSet(final TestContext ctx) {

        // GIVEN an adapter
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, false,
                s -> ctx.fail("should not have invoked onStartupSuccess"));

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked, see ctx.fail
        startup.await();
    }

    /**
     * Verifies that the <em>onStartupSuccess</em> method is not invoked if a client provided coap server fails to
     * start.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final TestContext ctx) {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        final CoapServer server = getCoapServer(true);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true,
                s -> ctx.fail("should not have invoked onStartupSuccess"));

        // WHEN starting the adapter
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked, see ctx.fail
        startup.await();
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.03 result if the device belongs to a tenant for
     * which the adapter is disabled.
     */
    @Test
    public void testUploadTelemetryFailsForDisabledTenant() {

        // GIVEN an adapter
        final DownstreamSender sender = mock(DownstreamSender.class);
        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapterConfiguration(new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, ADAPTER_TYPE)
                .put(TenantConstants.FIELD_ENABLED, false));
        when(tenantClient.get("my-tenant", null)).thenReturn(Future.succeededFuture(myTenantConfig));
        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device that belongs to "my-tenant" publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("my-tenant", "the-device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(ctx, authenticatedDevice, authenticatedDevice, false);

        // THEN the device gets a 4.03

        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(coapExchange).respond(captor.capture());
        assertThat("response with forbidden", captor.getValue().getCode(), is(ResponseCode.FORBIDDEN));

        // and the message has not been forwarded downstream
        verify(sender, never()).send(any(Message.class));
    }

    /**
     * Verifies that the adapter waits for an event being send with wait for outcome before responding with a 2.04
     * status to the device.
     */
    @Test
    public void testUploadEventWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);

        adapter.uploadEventMessage(ctx, authenticatedDevice, authenticatedDevice);

        // THEN the device does not get a response
        verify(coapExchange, never()).respond(ResponseCode.CHANGED);

        // until the event has been accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(coapExchange).respond(ResponseCode.CHANGED);
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.00 result if it is rejected by the downstream
     * peer.
     */
    @Test
    public void testUploadEventFailsForRejectedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an event that is not accepted by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);

        adapter.uploadEventMessage(ctx, authenticatedDevice, authenticatedDevice);
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 4.00
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(coapExchange).respond(captor.capture());
        assertThat("response with bad request", captor.getValue().getCode(),
                is(ResponseCode.BAD_REQUEST));
    }

    /**
     * Verifies that the adapter waits for an telemetry message being send (without wait for outcome) before responding
     * with a 2.04 status to the device.
     */
    @Test
    public void testUploadTelemetry() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenATelemetrySender(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(ctx, authenticatedDevice, authenticatedDevice, false);

        // THEN the device does not get a response
        verify(coapExchange, never()).respond(ResponseCode.CHANGED);

        // until the telemetry message has been accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(coapExchange).respond(ResponseCode.CHANGED);
    }

    /**
     * Verifies that the adapter waits for an telemetry message being send with wait for outcome before responding with
     * a 2.04 status to the device.
     */
    @Test
    public void testUploadTelemetryWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenATelemetrySenderForOutcome(outcome);
        final CoapServer server = getCoapServer(false);

        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN a device publishes an telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);

        adapter.uploadTelemetryMessage(ctx, authenticatedDevice, authenticatedDevice, true);

        // THEN the device does not get a response
        verify(coapExchange, never()).respond(ResponseCode.CHANGED);

        // until the telemetry message has been accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(coapExchange).respond(ResponseCode.CHANGED);
    }

    /**
     * Verifies that a telemetry message is rejected due to the limit exceeded.
     *
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenATelemetrySender(outcome);

        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong()))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        // WHEN a device publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);
        adapter.uploadTelemetryMessage(ctx, authenticatedDevice, authenticatedDevice, false);

        // THEN the device gets a 4.29
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(coapExchange).respond(captor.capture());
        assertThat(captor.getValue().getCode(), is(ResponseCode.TOO_MANY_REQUESTS));
    }

    /**
     * Verifies that an event message is rejected due to the limit exceeded.
     *
     */
    @Test
    public void testMessageLimitExceededForAnEventMessage() {

        // GIVEN an adapter with a downstream event consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);

        final CoapServer server = getCoapServer(false);
        final AbstractVertxBasedCoapAdapter<CoapAdapterProperties> adapter = getAdapter(server, true, null);

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong()))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        // WHEN a device publishes an event message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext ctx = CoapContext.fromRequest(coapExchange);
        adapter.uploadEventMessage(ctx, authenticatedDevice, authenticatedDevice);

        // THEN the device gets a 4.29
        final ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(coapExchange).respond(captor.capture());
        assertThat(captor.getValue().getCode(), is(ResponseCode.TOO_MANY_REQUESTS));
    }

    private static CoapExchange newCoapExchange(final Buffer payload) {

        final OptionSet options = new OptionSet().setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = mock(CoapExchange.class);
        when(coapExchange.getRequestPayload()).thenReturn(payload.getBytes());
        when(coapExchange.getRequestOptions()).thenReturn(options);
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
     * @param complete {@code true}, if that adapter should be created with all hono clients set, {@code false}, if the
     *            adapter should be created, and all hono clients set, but the credentials client is not set.
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
        adapter.setMetrics(mock(CoapAdapterMetrics.class));
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.init(vertx, mock(Context.class));

        return adapter;
    }

    private void givenAnEventSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class))).thenReturn(outcome);

        when(downstreamSenderFactory.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    private void givenATelemetrySenderForOutcome(final Future<ProtonDelivery> outcome) {

        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.sendAndWaitForOutcome(any(Message.class))).thenReturn(outcome);

        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    private void givenATelemetrySender(final Future<ProtonDelivery> outcome) {

        final DownstreamSender sender = mock(DownstreamSender.class);
        when(sender.send(any(Message.class))).thenReturn(outcome);

        when(downstreamSenderFactory.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

}
