/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

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
 * If {@code healthCheckPort} is not defined in the configuration, this implementation does nothing.
 * <p>
 * <b>Usage</b>
 * <ol>
 * <li>Invoke {@link #registerHealthCheckResources(HealthCheckProvider)} to register readiness and liveness checks.</li>
 * <li>Invoke {@link #start()} to start the server</li>
 * <li>Before shutdown: invoke {@link #stop()} for a graceful shutdown.</li>
 * </ol>
 */
public final class HealthCheckServer implements Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(HealthCheckServer.class);

    private static final String URI_LIVENESS_PROBE  = "/liveness";
    private static final String URI_READINESS_PROBE = "/readiness";

    private HttpServer server;

    private HealthCheckHandler readinessHandler;
    private HealthCheckHandler livenessHandler;

    private Vertx vertx;
    private ApplicationConfigProperties config;
    private Router router;

    /**
     * Create a new HealthCheckServer for the given Vertx and configuration.
     *
     * @param vertx The vertx instance.
     * @param config The application configuration instance.
     * @throws NullPointerException if vertx is {@code null}.
     * @throws NullPointerException if config is {@code null}.
     */
    public HealthCheckServer(final Vertx vertx, final ApplicationConfigProperties config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);

        prepareHealthCheck();
    }

    /**
     *
     * Configures router and healthCheckHandlers.
     *
     */
    private void prepareHealthCheck() {
        if (config.getHealthCheckPort() != Constants.PORT_UNCONFIGURED) {
            readinessHandler = HealthCheckHandler.create(vertx);
            livenessHandler = HealthCheckHandler.create(vertx);
            router = Router.router(vertx);
            LOG.debug("Health check prepared.");
        } else {
            LOG.info("No health check configured.");
        }
    }

    /**
     * Registers the readiness and liveness checks of the given service if health check is configured, otherwise does
     * nothing.
     * 
     * @param serviceInstance instance of the service which's checks should be registered.
     */
    public void registerHealthCheckResources(final HealthCheckProvider serviceInstance) {
        if (router != null) {
            serviceInstance.registerLivenessChecks(livenessHandler);
            serviceInstance.registerReadinessChecks(readinessHandler);
        } // else: health check port not configured.
    }

    /**
     * Starts the health check server if health check is configured, otherwise does nothing.
     *
     * @return a future indicating the output of the operation.
     */
    @Override
    public Future<Void> start() {

        Future<Void> result = Future.future();
        if (router != null) {
            HttpServerOptions options = new HttpServerOptions()
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
                    LOG.warn("failed to start health checks HTTP server:", startAttempt.cause().getMessage());
                    result.fail(startAttempt.cause());
                }
            });

        } else { // health check port not configured
            result.complete();
        }
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

        Future<Void> result = Future.future();
        if (server != null) {
            LOG.info("closing health check HTTP server [{}:{}]", config.getHealthCheckBindAddress(), server.actualPort());
            server.close(result.completer());
        } else {
            result.complete();
        }
        return result;
    }

}
