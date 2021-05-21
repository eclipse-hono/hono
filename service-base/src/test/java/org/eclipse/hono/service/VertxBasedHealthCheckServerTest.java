/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
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

    private static final String HOST = Constants.LOOPBACK_DEVICE_ADDRESS;
    private static final String KEY_STORE_PATH = "target/certs/authServerKeyStore.p12";
    private static final String STORE_PASSWORD = "authkeys";
    private VertxBasedHealthCheckServer server = null;

    @AfterEach
    void cleanup(final VertxTestContext ctx) {
        if (server == null) {
            ctx.completeNow();
        } else {
            server.stop().onComplete(r -> ctx.completeNow());
        }
    }

    /**
     * Verifies that a health check server fails to start if it is configured
     * to bind to the loop back device.
     *
     * @param ctx The test context.
     */
    @Test
    void testHealthCheckServerWithNoBindAddressFailsToStart(final Vertx vertx, final VertxTestContext ctx) {

        final ServerConfig config = new ServerConfig();

        server = new VertxBasedHealthCheckServer(vertx, config);

        server.start().onComplete(ctx.failing(error -> ctx.completeNow()));
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

        final Checkpoint testsDone = ctx.checkpoint();
        server = new VertxBasedHealthCheckServer(vertx, config);
        server.setBindToLoopbackDeviceAllowed(false, true);
        registerHealthCheckCheckpoints(ctx, server, 1);

        server.start()
            .map(ok -> {
                ctx.verify(() -> assertThat(server.getPort()).isEqualTo(Constants.PORT_UNCONFIGURED));
                return ok;
            })
            .map(ok -> getWebClient(vertx, server, false))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
            .onComplete(ctx.succeeding(v -> testsDone.flag()));
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

        final Checkpoint testsDone = ctx.checkpoint();
        server = new VertxBasedHealthCheckServer(vertx, config);
        server.setBindToLoopbackDeviceAllowed(true, false);
        registerHealthCheckCheckpoints(ctx, server, 1);

        server.start()
            .map(ok -> {
                ctx.verify(() -> assertThat(server.getInsecurePort()).isEqualTo(Constants.PORT_UNCONFIGURED));
                return ok;
            })
            .map(ok -> getWebClient(vertx, server, true))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
            .onComplete(ctx.succeeding(v -> testsDone.flag()));
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

        final Checkpoint testsDone = ctx.checkpoint();
        server = new VertxBasedHealthCheckServer(vertx, config);
        server.setBindToLoopbackDeviceAllowed(true, true);
        registerHealthCheckCheckpoints(ctx, server, 2);

        server.start()
            .map(ok -> getWebClient(vertx, server, true))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
            .compose(result -> Future.succeededFuture(getWebClient(vertx, server, false)))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/liveness"))
            .compose(httpClient -> checkHealth(ctx, httpClient, "/readiness"))
            .onComplete(ctx.succeeding(v -> testsDone.flag()));
    }

    private void registerHealthCheckCheckpoints(final VertxTestContext ctx, final VertxBasedHealthCheckServer server,
            final int checkpointPasses) {

        final Checkpoint callLivenessCheckpoint = ctx.checkpoint(checkpointPasses);
        final Checkpoint callReadinessCheckpoint = ctx.checkpoint(checkpointPasses);

        server.registerHealthCheckResources(new HealthCheckProvider() {

            @Override
            public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
                readinessHandler.register("readiness-insecure", event -> {
                    callReadinessCheckpoint.flag();
                    event.tryComplete(Status.OK());
                });
            }

            @Override
            public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
                livenessHandler.register("liveness-insecure", event -> {
                    callLivenessCheckpoint.flag();
                    event.tryComplete(Status.OK());
                });
            }
        });
    }

    private Future<WebClient> checkHealth(final VertxTestContext ctx, final WebClient httpClient,
            final String endpoint) {
        final Promise<WebClient> sentHealth = Promise.promise();
        httpClient.get(endpoint)
                .expect(ResponsePredicate.status(HttpURLConnection.HTTP_OK))
                .send(result -> {
                    if (result.failed()) {
                        ctx.failNow(result.cause());
                    }
                    sentHealth.complete(httpClient);
                });

        return sentHealth.future();
    }

    private WebClient getWebClient(final Vertx vertx, final VertxBasedHealthCheckServer server, final boolean secure) {

        final WebClientOptions options = new WebClientOptions()
                .setDefaultHost(HOST)
                .setDefaultPort(secure ? server.getPort() : server.getInsecurePort())
                .setTrustAll(secure)
                .setVerifyHost(false)
                .setSsl(secure);

        return WebClient.create(vertx, options);
    }

}
