/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Router;

/**
 * Provides a HTTP server for health checks. Requires an instance of {@link ServerConfig} for its
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
    private static final int DEFAULT_PORT = 8088;

    private boolean bindSecureServerToLoopbackDeviceAllowed = false;
    private boolean bindInsecureServerToLoopbackDeviceAllowed = false;
    private HttpServer server;
    private HttpServer insecureServer;

    private final HealthCheckHandler readinessHandler;
    private final HealthCheckHandler livenessHandler;

    private final Vertx vertx;
    private final ServerConfig config;
    private Router router;
    private final List<Handler<Router>> additionalResources = new ArrayList<>();

    /**
     * Create a new VertxBasedHealthCheckServer for the given Vertx and configuration.
     *
     * @param vertx The vertx instance.
     * @param config The application configuration instance.
     * @throws NullPointerException if vertx is {@code null}.
     * @throws NullPointerException if config is {@code null}.
     */
    public VertxBasedHealthCheckServer(final Vertx vertx, final ServerConfig config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);

        readinessHandler = HealthCheckHandler.create(this.vertx);
        livenessHandler = HealthCheckHandler.create(this.vertx);
        router = Router.router(this.vertx);
    }

    void setBindToLoopbackDeviceAllowed(final boolean secureServerAllowed, final boolean insecureServerAllowed) {
        this.bindSecureServerToLoopbackDeviceAllowed = secureServerAllowed;
        this.bindInsecureServerToLoopbackDeviceAllowed = insecureServerAllowed;
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
     * Sets providers of additional resources to be exposed by this health check server.
     * <p>
     * During start up, each of the providers will be invoked with the HTTP server's
     * {@code Router} so that the providers can register their resources.
     * 
     * @param resourceProviders Additional resources to expose.
     * @throws NullPointerException if provider list is {@code null}.
     */
    @Autowired(required = false)
    @Qualifier("healthchecks")
    public void setAdditionalResources(final List<Handler<Router>> resourceProviders) {
        Objects.requireNonNull(resourceProviders);
        this.additionalResources.addAll(resourceProviders);
    }

    /**
     * Starts the health check server.
     *
     * @return a future indicating the output of the operation.
     */
    @Override
    public Future<Void> start() {

        registerAdditionalResources();

        return CompositeFuture.any(bindSecureHttpServer(), bindInsecureHttpServer())
                .map(ok -> (Void) null)
                .recover(error -> {
                    LOG.error("failed to start Health Check server", error);
                    return Future.failedFuture(error);
                });
    }

    private Future<Void> bindInsecureHttpServer() {

        final Promise<Void> result = Promise.promise();

        if (Constants.LOOPBACK_DEVICE_ADDRESS.equals(config.getInsecurePortBindAddress())) {
            if (bindInsecureServerToLoopbackDeviceAllowed) {
                LOG.warn("insecure health checks HTTP server will bind to loopback device only");
            } else {
                LOG.info("won't start insecure health checks HTTP server: no bind address configured.");
                return Future.failedFuture("no bind address configured for insecure server");
            }
        }

        final HttpServerOptions options = new HttpServerOptions()
                .setPort(config.getInsecurePort(DEFAULT_PORT))
                .setHost(config.getInsecurePortBindAddress());
        insecureServer = vertx.createHttpServer(options);

        router.get(URI_READINESS_PROBE).handler(readinessHandler);
        router.get(URI_LIVENESS_PROBE).handler(livenessHandler);

        insecureServer.requestHandler(router).listen(startAttempt -> {
            if (startAttempt.succeeded()) {
                LOG.info("successfully started insecure health checks HTTP server");
                LOG.info("readiness probe available at http://{}:{}{}", options.getHost(), insecureServer.actualPort(),
                        URI_READINESS_PROBE);
                LOG.info("liveness probe available at http://{}:{}{}", options.getHost(), insecureServer.actualPort(),
                        URI_LIVENESS_PROBE);
                result.complete();
            } else {
                LOG.warn("failed to start insecure health checks HTTP server: {}",
                        startAttempt.cause().getMessage());
                result.fail(startAttempt.cause());
            }
        });
        return result.future();
    }

    private Future<Void> bindSecureHttpServer() {

        if (config.isSecurePortEnabled()) {

            if (Constants.LOOPBACK_DEVICE_ADDRESS.equals(config.getBindAddress())) {
                if (bindSecureServerToLoopbackDeviceAllowed) {
                    LOG.warn("secure health checks HTTP server will bind to loopback device only");
                } else {
                    LOG.info("won't start secure health checks HTTP server: no bind address configured.");
                    return Future.failedFuture("no bind address configured for secure server");
                }
            }

            final Promise<Void> result = Promise.promise();

            final HttpServerOptions options = new HttpServerOptions()
                    .setPort(config.getPort(DEFAULT_PORT))
                    .setHost(config.getBindAddress())
                    .setKeyCertOptions(config.getKeyCertOptions())
                    .setSsl(true);
            server = vertx.createHttpServer(options);

            router.get(URI_READINESS_PROBE).handler(readinessHandler);
            router.get(URI_LIVENESS_PROBE).handler(livenessHandler);

            server.requestHandler(router).listen(startAttempt -> {
                if (startAttempt.succeeded()) {
                    LOG.info("successfully started secure health checks HTTP server");
                    LOG.info("readiness probe available at https://{}:{}{}", options.getHost(), server.actualPort(),
                            URI_READINESS_PROBE);
                    LOG.info("liveness probe available at https://{}:{}{}", options.getHost(), server.actualPort(),
                            URI_LIVENESS_PROBE);
                    result.complete();
                } else {
                    LOG.warn("failed to start secure health checks HTTP server: {}", startAttempt.cause().getMessage());
                    result.fail(startAttempt.cause());
                }
            });
            return result.future();
        } else {
            LOG.warn("cannot start secure health checks HTTP server: no key material configured");
            return Future.failedFuture("no key material configured for secure server");
        }
    }

    private void registerAdditionalResources() {
        this.additionalResources.forEach(handler -> {
            LOG.info("registering additional resource: {}", handler);
            handler.handle(router);
        });
        additionalResources.clear();
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
        final Promise<Void> serverStopTracker = Promise.promise();
        if (server != null) {
            LOG.info("closing secure health check HTTP server [{}:{}]", config.getBindAddress(), server.actualPort());
            server.close(serverStopTracker);
        } else {
            serverStopTracker.complete();
        }

        final Promise<Void> insecureServerStopTracker = Promise.promise();
        if (insecureServer != null) {
            LOG.info("closing insecure health check HTTP server [{}:{}]", config.getInsecurePortBindAddress(),
                    insecureServer.actualPort());
            insecureServer.close(insecureServerStopTracker);
        } else {
            insecureServerStopTracker.complete();
        }

        return CompositeFuture.all(serverStopTracker.future(), insecureServerStopTracker.future())
                .map(ok -> (Void) null)
                .recover(t -> Future.failedFuture(t));
    }

    int getInsecurePort() {
        return Optional.ofNullable(insecureServer)
                .map(s -> s.actualPort())
                .orElse(Constants.PORT_UNCONFIGURED);
    }

    int getPort() {
        return Optional.ofNullable(server)
                .map(s -> s.actualPort())
                .orElse(Constants.PORT_UNCONFIGURED);
    }

}
