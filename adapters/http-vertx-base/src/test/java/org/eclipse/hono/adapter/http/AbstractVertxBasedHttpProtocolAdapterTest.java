/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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

/**
 * Verifies behavior of {@link AbstractVertxBasedHttpProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedHttpProtocolAdapterTest {

    HonoClient messagingClient;
    HonoClient registrationClient;
    HonoClientBasedAuthProvider credentialsAuthProvider;
    HttpProtocolAdapterProperties config;

    /**
     * Sets up common fixture.
     */
    @Before
    public void setup() {

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsAuthProvider = mock(HonoClientBasedAuthProvider.class);
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
        startup.await(300);
        verify(server).requestHandler(any(Handler.class));
        verify(server).listen(any(Handler.class));
        verify(messagingClient).connect(any(ProtonClientOptions.class), any(Handler.class), any(Handler.class));
        verify(registrationClient).connect(any(ProtonClientOptions.class), any(Handler.class), any(Handler.class));
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
        startup.await(300);
        onStartupSuccess.await(300);
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
        startup.await(300);
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
        Async startupFailed = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(startupAttempt -> {
            ctx.assertTrue(startupAttempt.failed());
            startupFailed.complete();
        });
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
        startupFailed.await(300);
    }

    /**
     * Verifies that the adapter does not include a device registration assertion in an HTTP response by default.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionHeaderDoesNotIncludeAssertionInResponse(final TestContext ctx) {

        // GIVEN an adapter connected to the Device Registration service
        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);

        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> startup.complete()));
        adapter.start(startupTracker);
        startup.await(300);

        // WHEN a request to publish telemetry data is processed
        HttpServerRequest req = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(req);
        when(context.response()).thenReturn(response);
        adapter.getRegistrationAssertionHeader(context, "tenant", "device");

        // THEN the response does NOT contain a registration assertion header
        verify(response, never()).putHeader(eq(AbstractVertxBasedHttpProtocolAdapter.HEADER_REGISTRATION_ASSERTION), anyString());
    }

    /**
     * Verifies that the adapter adds a device registration assertion in an HTTP response if configured.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetRegistrationAssertionHeaderAddsAssertionToResponse(final TestContext ctx) {

        // GIVEN an adapter connected to the Device Registration service that is configured to include
        // device registration assertions in responses
        config.setRegAssertionEnabled(true);
        HttpServer server = getHttpServer(false);
        AbstractVertxBasedHttpProtocolAdapter<HttpProtocolAdapterProperties> adapter = getAdapter(server, null);
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);

        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> startup.complete()));
        adapter.start(startupTracker);
        startup.await(300);

        // WHEN a request to publish telemetry data is processed
        HttpServerRequest req = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);
        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(req);
        when(context.response()).thenReturn(response);
        adapter.getRegistrationAssertionHeader(context, "tenant", "device");

        // THEN the response contains a registration assertion header
        verify(response).putHeader(AbstractVertxBasedHttpProtocolAdapter.HEADER_REGISTRATION_ASSERTION, "token");
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
        adapter.setRegistrationServiceClient(registrationClient);
        adapter.setMetrics(mock(HttpAdapterMetrics.class));

        RegistrationClient regClient = mock(RegistrationClient.class);
        doAnswer(invocation -> {
            Handler<AsyncResult<RegistrationResult>> resultHandler = invocation.getArgumentAt(1, Handler.class);
            JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
            resultHandler.handle(Future.succeededFuture(RegistrationResult.from(200, result)));
            return null;
        }).when(regClient).assertRegistration(anyString(), any(Handler.class));

        doAnswer(invocation -> {
            Handler<AsyncResult<RegistrationClient>> resultHandler = invocation.getArgumentAt(1, Handler.class);
            resultHandler.handle(Future.succeededFuture(regClient));
            return registrationClient;
        }).when(registrationClient).getOrCreateRegistrationClient(anyString(), any(Handler.class));

        return adapter;
    }
}
