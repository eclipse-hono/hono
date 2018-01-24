/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.http;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.util.RegistrationConstants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link AbstractVertxBasedHttpProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedHttpProtocolAdapterTest {

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(5);

    private HonoClient                    messagingClient;
    private HonoClient                    registrationClient;
    private HonoClientBasedAuthProvider   credentialsAuthProvider;
    private HttpProtocolAdapterProperties config;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsAuthProvider = mock(HonoClientBasedAuthProvider.class);
        when(credentialsAuthProvider.start()).thenReturn(Future.succeededFuture());
        config = new HttpProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);
    }

    /**
     * Verifies that a client provided http server is started instead of creating and starting a new http server.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     * @throws Exception if the test fails.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartUsesClientProvidedHttpServer(final TestContext ctx) throws Exception {

        // GIVEN an adapter with a client provided http server
        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);

        // WHEN starting the adapter
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the client provided http server has been configured and started
        startup.await();
        verify(server).requestHandler(any(Handler.class));
        verify(server).listen(any(Handler.class));
        verify(messagingClient).connect(any(ProtonClientOptions.class), any(Handler.class));
        verify(registrationClient).connect(any(ProtonClientOptions.class), any(Handler.class));
    }

    /**
     * Verifies that the <me>onStartupSuccess</em> method is invoked if the http server has been started successfully.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     * @throws Exception if the test fails.
     */
    @Test
    public void testStartInvokesOnStartupSuccess(final TestContext ctx) throws Exception {

        // GIVEN an adapter with a client provided http server
        HttpServer server = getHttpServer(false);
        Async onStartupSuccess = ctx.async();

        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, s -> onStartupSuccess.complete());
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);
        adapter.setMetrics(mock(HttpAdapterMetrics.class));

        // WHEN starting the adapter
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
        startup.await();
        onStartupSuccess.await();
    }


    /**
     * Verifies that the <me>onStartupSuccess</em> method is not invoked if no credentials authentication provider is set.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartUpFailsIfCredentialsAuthProviderIsNotSet(final TestContext ctx) {

        // GIVEN an adapter with a client provided http server
        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, s -> ctx.fail("should not have invoked onStartupSuccess"));

        // WHEN starting the adapter
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
        startup.await();
    }

    /**
     * Verifies that the <me>onStartupSuccess</em> method is not invoked if a client provided http server fails to start.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final TestContext ctx) {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        HttpServer server = getHttpServer(true);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, s -> ctx.fail("should not invoke onStartupSuccess"));

        // WHEN starting the adapter
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure());
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has not been invoked
    }

    /**
     * Verifies that the adapter waits for an event being settled and accepted
     * by a downstream peer before responding with a 202 status to the device.
     */
    @Test
    public void testUploadEventWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);

        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final RoutingContext ctx = newRoutingContext(payload, response);

        adapter.uploadEventMessage(ctx, "tenant", "device", payload, "application/text");

        // THEN the device does not get a response
        verify(response, never()).end();

        // until the event has been accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(response).setStatusCode(202);
        verify(response).end();
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

        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes an event that is not accepted by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final RoutingContext ctx = newRoutingContext(payload);

        adapter.uploadEventMessage(ctx, "tenant", "device", payload, "application/text");
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 400
        verify(ctx).fail(HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that the adapter does not wait for a telemetry message being settled and accepted
     * by a downstream peer before responding with a 202 status to the device.
     */
    @Test
    public void testUploadTelemetryDoesNotWaitForAcceptedOutcome() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        final Future<ProtonDelivery> outcome = Future.succeededFuture(mock(ProtonDelivery.class));
        givenATelemetrySenderForOutcome(outcome);

        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);

        // WHEN a device publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final HttpServerResponse response = mock(HttpServerResponse.class);
        final RoutingContext ctx = newRoutingContext(payload, response);

        adapter.uploadTelemetryMessage(ctx, "tenant", "device", payload, "application/text");

        // THEN the device receives a 202 response immediately
        verify(response).setStatusCode(202);
        verify(response).end();
    }

    private static RoutingContext newRoutingContext(final Buffer payload) {
        return newRoutingContext(payload, mock(HttpServerResponse.class));
    }

    private static RoutingContext newRoutingContext(final Buffer payload, final HttpServerResponse response) {

        final HttpServerRequest request = mock(HttpServerRequest.class);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.getBody()).thenReturn(payload);
        when(ctx.response()).thenReturn(response);
        when(ctx.request()).thenReturn(request);
        when(response.setStatusCode(anyInt())).thenReturn(response);
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private HttpServer getHttpServer(final boolean startupShouldFail) {

        HttpServer server = mock(HttpServer.class);
        when(server.actualPort()).thenReturn(0, 8080);
        when(server.requestHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            Handler<AsyncResult<HttpServer>> handler = (Handler<AsyncResult<HttpServer>>) invocation.getArgumentAt(0, Handler.class);
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
    @SuppressWarnings("unchecked")
    private AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> getAdapter(final HttpServer server, final Handler<Void> onStartupSuccess) {

        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = new AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties>() {

            @Override
            protected String getTypeName() {
                return "http";
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

        adapter.setConfig(config);
        adapter.setInsecureHttpServer(server);
        adapter.setHonoMessagingClient(messagingClient);
        when(messagingClient.connect(any(ProtonClientOptions.class), any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));
        adapter.setRegistrationServiceClient(registrationClient);
        when(registrationClient.connect(any(ProtonClientOptions.class), any(Handler.class))).thenReturn(Future.succeededFuture(registrationClient));
        adapter.setMetrics(mock(HttpAdapterMetrics.class));

        final RegistrationClient regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
        when(regClient.assertRegistration(anyString(), anyString())).thenReturn(Future.succeededFuture(result));

        when(registrationClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        return adapter;
    }

    private void givenAnEventSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.send(any(Message.class))).thenReturn(outcome);

        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    private void givenATelemetrySenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.send(any(Message.class))).thenReturn(outcome);

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

}
