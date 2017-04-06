/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import static org.mockito.Mockito.*;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.HonoConfigProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.proton.ProtonClientOptions;

/**
 * Verifies behavior of {@link AbstractVertxBasedHttpProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedHttpProtocolAdapterTest {

    HonoClient honoClient;

    /**
     * Creates a 
     */
    @Before
    public void setup() {

        honoClient = mock(HonoClient.class);
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
        AbstractVertxBasedHttpProtocolAdapter adapter = getAdapter(mock(Router.class), null);
        adapter.setConfig(new HonoConfigProperties());
        adapter.setHttpServer(server);
        adapter.setHonoClient(honoClient);

        // WHEN starting the adapter
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(startupAttempt -> {
            ctx.assertTrue(startupAttempt.succeeded());
            startup.complete();
        });
        adapter.start(startupTracker);

        // THEN the client provided http server has been configured and started
        startup.await(300);
        verify(server).requestHandler(any(Handler.class));
        verify(server).listen(any(Handler.class));
        verify(honoClient).connect(any(ProtonClientOptions.class), any(Handler.class));
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

        AbstractVertxBasedHttpProtocolAdapter adapter = new AbstractVertxBasedHttpProtocolAdapter() {

            @Override
            protected void addRoutes(final Router router) {
            }

            @Override
            protected void onStartupSuccess() {
                onStartupSuccess.complete();
            }
        };

        adapter.setConfig(new HonoConfigProperties());
        adapter.setHttpServer(server);
        adapter.setHonoClient(honoClient);

        // WHEN starting the adapter
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(startupAttempt -> {
            ctx.assertTrue(startupAttempt.succeeded());
            startup.complete();
        });
        adapter.start(startupTracker);

        // THEN the onStartupSuccess method has been invoked
        startup.await(300);
        onStartupSuccess.await(300);
    }

    /**
     * Verifies that the <me>onStartupSuccess</em> method is invoked if the http server has been started successfully.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     * @throws Exception if the test fails.
     */
    @Test
    public void testStartDoesNotInvokeOnStartupSuccessIfStartupFails(final TestContext ctx) throws Exception {

        // GIVEN an adapter with a client provided http server that fails to bind to a socket when started
        HttpServer server = getHttpServer(true);

        AbstractVertxBasedHttpProtocolAdapter adapter = new AbstractVertxBasedHttpProtocolAdapter() {

            @Override
            protected void addRoutes(final Router router) {
            }

            @Override
            protected void onStartupSuccess() {
                ctx.fail("should not invoke onStartupSuccess");
            }
        };

        adapter.setConfig(new HonoConfigProperties());
        adapter.setHttpServer(server);
        adapter.setHonoClient(honoClient);

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

    private AbstractVertxBasedHttpProtocolAdapter getAdapter(final Router router, final Handler<Router> routeRegistrator) {

        return new AbstractVertxBasedHttpProtocolAdapter() {

            @Override
            protected void addRoutes(final Router router) {

                if (routeRegistrator != null) {
                    routeRegistrator.handle(router);
                }
            }

            @Override
            protected Router createRouter() {
                return router;
            }
        };
    }
}
