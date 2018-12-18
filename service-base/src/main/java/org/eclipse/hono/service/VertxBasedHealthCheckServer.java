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

package org.eclipse.hono.service;

import java.util.Objects;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Router;

/**
 * Provides a HTTP server for health checks. Requires an instance of {@link ApplicationConfigProperties} for it's
 * configuration.
 * <p>
 * <b>Usage</b>
 * <ol>
 * <li>Invoke {@link #registerHealthCheckResources(HealthCheckProvider)} to register readiness and liveness checks.</li>
 * <li>Invoke {@link #start()} to start the server</li>
 * <li>Before shutdown: invoke {@link #stop()} for a graceful shutdown.</li>
 * </ol>
 */
public final class VertxBasedHealthCheckServer implements HealthCheckServer {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedHealthCheckServer.class);

    private static final String URI_LIVENESS_PROBE = "/liveness";
    private static final String URI_READINESS_PROBE = "/readiness";

    private HttpServer server;

    private final HealthCheckHandler readinessHandler;
    private final HealthCheckHandler livenessHandler;

    private final Vertx vertx;
    private final ApplicationConfigProperties config;
    private Router router;

    /**
     * Create a new VertxBasedHealthCheckServer for the given Vertx and configuration.
     *
     * @param vertx The vertx instance.
     * @param config The application configuration instance.
     * @throws NullPointerException if vertx is {@code null}.
     * @throws NullPointerException if config is {@code null}.
     * @throws IllegalArgumentException if health check port is not configured.
     */
    public VertxBasedHealthCheckServer(final Vertx vertx, final ApplicationConfigProperties config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
        if (config.getHealthCheckPort() == Constants.PORT_UNCONFIGURED) {
            throw new IllegalArgumentException("Health check port not configured");
        }

        readinessHandler = HealthCheckHandler.create(this.vertx);
        livenessHandler = HealthCheckHandler.create(this.vertx);
        router = Router.router(this.vertx);
    }

    /**
     * Registers the readiness and liveness checks of the given service.
     * 
     * @param serviceInstance instance of the service which's checks should be registered.
     */
    @Override
    public void registerHealthCheckResources(final HealthCheckProvider serviceInstance) {
        serviceInstance.registerLivenessChecks(livenessHandler);
        serviceInstance.registerReadinessChecks(readinessHandler);
    }

    /**
     * Starts the health check server.
     *
     * @return a future indicating the output of the operation.
     */
    @Override
    public Future<Void> start() {

        final Future<Void> result = Future.future();
        final HttpServerOptions options = new HttpServerOptions()
                .setPort(config.getHealthCheckPort())
                .setHost(config.getHealthCheckBindAddress());
        server = vertx.createHttpServer(options);

        router.get(URI_READINESS_PROBE).handler(readinessHandler);
        router.get(URI_LIVENESS_PROBE).handler(livenessHandler);

        server.requestHandler(router::accept).listen(startAttempt -> {
            if (startAttempt.succeeded()) {
                LOG.info("readiness probe available at http://{}:{}{}", options.getHost(), options.getPort(),
                        URI_READINESS_PROBE);
                LOG.info("liveness probe available at http://{}:{}{}", options.getHost(), options.getPort(),
                        URI_LIVENESS_PROBE);
                result.complete();
            } else {
                LOG.warn("failed to start health checks HTTP server: {}", startAttempt.cause().getMessage());
                result.fail(startAttempt.cause());
            }
        });
        return result;
    }

    /**
     * Closes the HTTP server exposing the health checks.
     * <p>
     * This method usually does not need to be invoked explicitly because
     * the HTTP server will be closed implicitly when the vert.x instance
     * is closed.
     * 
     * @return A Future indicating the outcome of the operation.
     */
    @Override
    public Future<Void> stop() {

        final Future<Void> result = Future.future();
        if (server != null) {
            LOG.info("closing health check HTTP server [{}:{}]", config.getHealthCheckBindAddress(), server.actualPort());
            server.close(result.completer());
        } else {
            result.complete();
        }
        return result;
    }

}
