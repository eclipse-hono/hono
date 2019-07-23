/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.config.ServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying the behavior of {@code VertxBasedHealthCheckServer}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class VertxBasedHealthCheckServerTest {

    private static final String HOST = "localhost";
    private static final String KEY_STORE_PATH = "target/certs/authServerKeyStore.p12";
    private static final String STORE_PASSWORD = "authkeys";
    private VertxBasedHealthCheckServer server = null;

    @AfterEach
    void cleanup(final VertxTestContext ctx) {
        if (server != null) {
            final Checkpoint checkpoint = ctx.checkpoint();
            server.stop().setHandler(r -> checkpoint.flag());
        }
    }

    /**
     * Tests that a health check server can be configured with an insecure port.
     *
     * @param ctx The test context.
     */
    @Test
    void testHealthCheckServerWithNoBindAddressFailsToStart(final Vertx vertx, final VertxTestContext ctx) {

        final ServerConfig config = new ServerConfig();

        server = new VertxBasedHealthCheckServer(vertx, config);
        registerHealthChecks(ctx, server, 1);

        server.start().setHandler(ctx.failing(error -> ctx.completeNow()));
    }


    /**
     * Tests that a health check server can be configured with an insecure port.
     *
     * @param ctx The test context.
     */
    @Test
    void testHealthCheckServerWithInsecureEndpoint(final Vertx vertx, final VertxTestContext ctx) {

        final ServerConfig config = new ServerConfig();
        config.setInsecurePort(0);
        config.setInsecurePortBindAddress(HOST);

        server = new VertxBasedHealthCheckServer(vertx, config);
        registerHealthChecks(ctx, server, 1);

        server.start().compose(result -> Future.succeededFuture(getWebClient(vertx, server, false)))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
                .setHandler(ctx.completing());
    }

    /**
     * Tests that a health check server can be configured with a secure port.
     *
     * @param ctx The test context.
     */
    @Test
    void testHealthCheckServerWithSecureEndpoint(final Vertx vertx, final VertxTestContext ctx) {

        final ServerConfig config = new ServerConfig();
        config.setPort(0);
        config.setBindAddress(HOST);
        config.setKeyStorePath(KEY_STORE_PATH);
        config.setKeyStorePassword(STORE_PASSWORD);

        server = new VertxBasedHealthCheckServer(vertx, config);
        registerHealthChecks(ctx, server, 1);

        server.start().compose(result -> Future.succeededFuture(getWebClient(vertx, server, true)))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
                .setHandler(ctx.completing());
    }

    /**
     * Tests that a health check server can be configured with a secure port and an insecure endpoint at the same time.
     *
     * @param ctx The test context.
     */
    @Test
    void testHealthCheckServerWithSecureAndInsecureEndpoint(final Vertx vertx, final VertxTestContext ctx) {

        final ServerConfig config = new ServerConfig();
        config.setPort(0);
        config.setBindAddress(HOST);
        config.setInsecurePort(0);
        config.setInsecurePortBindAddress(HOST);
        config.setKeyStorePath(KEY_STORE_PATH);
        config.setKeyStorePassword(STORE_PASSWORD);

        server = new VertxBasedHealthCheckServer(vertx, config);
        registerHealthChecks(ctx, server, 2);

        server.start().compose(result -> Future.succeededFuture(getWebClient(vertx, server, true)))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
                .compose(result -> Future.succeededFuture(getWebClient(vertx, server, false)))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
                .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
                .setHandler(ctx.completing());
    }

    private void registerHealthChecks(final VertxTestContext ctx, final VertxBasedHealthCheckServer server,
            final int checkpoints) {
        final Checkpoint callLivenessCheckpoint = ctx.checkpoint(checkpoints);
        final Checkpoint callReadinessCheckpoint = ctx.checkpoint(checkpoints);

        server.registerHealthCheckResources(new HealthCheckProvider() {

            @Override
            public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
                readinessHandler.register("readiness-insecure", event -> {
                    callReadinessCheckpoint.flag();
                    event.complete();
                });
            }

            @Override
            public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
                livenessHandler.register("liveness-insecure", event -> {
                    callLivenessCheckpoint.flag();
                    event.complete();
                });
            }
        });
    }

    private Future<WebClient> checkHealth(final VertxTestContext ctx, final WebClient httpClient,
            final String endpoint) {
        final Future<WebClient> sentHealth = Future.future();
        httpClient.get(endpoint)
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_OK))
                .send(result -> {
                    if (result.failed()) {
                        ctx.failNow(result.cause());
                    }
                    sentHealth.complete(httpClient);
                });

        return sentHealth;
    }

    private WebClient getWebClient(final Vertx vertx, final VertxBasedHealthCheckServer server, final boolean secure) {
        final WebClientOptions options = new WebClientOptions()
                .setDefaultHost(HOST)
                .setDefaultPort(secure ? server.getPort() : server.getInsecurePort())
                .setTrustAll(secure)
                .setSsl(secure);

        return WebClient.create(vertx, options);
    }

}
