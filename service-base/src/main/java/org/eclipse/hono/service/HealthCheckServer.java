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

import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
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

    /**
     * The vert.x event bus address to readiness messages are published.
     */
    public static final String EVENT_BUS_ADDRESS_HEALTH_READINESS = "health.readiness";
    public static final String EVENT_BUS_ADDRESS_HEALTH_LIVENESS = "health.liveness";

    public static final String MSG_FIELD_VERTICLE_NAME = "verticle-name";
    public static final String MSG_FIELD_VERTICLE_INSTANCE_ID = "verticle-instance-id";
    public static final String MSG_FIELD_PROCEDURE_NAME = "procedure-name";
    public static final String MSG_FIELD_LAST_SEEN = "last-seen";
    public static final long LIVENESS_TIMEOUT_SECONDS = 60; // TODO make configurable

    private HttpServer server;

    private HealthCheckHandler readinessHandler;
    private HealthCheckHandler livenessHandler;

    private final Vertx vertx;
    private final ApplicationConfigProperties config;
    private Router router;

    private MessageConsumer<JsonObject> readinessConsumer;
    private MessageConsumer<JsonObject> livenessConsumer;

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

    private void registerConsumers() {
        readinessConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_HEALTH_READINESS);
        readinessConsumer.handler(this::processReadinessMessage);
        LOG.info("listening on event bus [address: {}] for readiness status", EVENT_BUS_ADDRESS_HEALTH_READINESS);

        livenessConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_HEALTH_LIVENESS);
        livenessConsumer.handler(this::processLivenessMessage);
        LOG.info("listening on event bus [address: {}] for liveness status", EVENT_BUS_ADDRESS_HEALTH_LIVENESS);
    }

    private void processReadinessMessage(final Message<JsonObject> msg) {

        final String verticleName = msg.headers().get(MSG_FIELD_VERTICLE_NAME);
        final String instanceId = msg.headers().get(MSG_FIELD_VERTICLE_INSTANCE_ID);
        final String procedureName = msg.headers().get(MSG_FIELD_PROCEDURE_NAME);
        final Status status = new Status(msg.body());

        LOG.info("received readiness update {}/{}/{}: {}", verticleName, instanceId, procedureName, status.isOk());
        if (LOG.isDebugEnabled()) {
            LOG.debug("readiness update status: {}", msg.body().encodePrettily());
        }

        readinessHandler.register(verticleName + "/" + instanceId + "/" + procedureName,
                statusFuture -> statusFuture.complete(status)
                );
    }

    private void processLivenessMessage(final Message<JsonObject> msg) {

        final String verticleName = msg.headers().get(MSG_FIELD_VERTICLE_NAME);
        final String instanceId = msg.headers().get(MSG_FIELD_VERTICLE_INSTANCE_ID);
        final String procedureName = msg.headers().get(MSG_FIELD_PROCEDURE_NAME);
        final String lastSeen = msg.headers().get(MSG_FIELD_LAST_SEEN);
        final Status status = new Status(msg.body());

        if (LOG.isDebugEnabled()) {
            LOG.debug("received liveness update {}/{}/{}: {}", verticleName, instanceId, procedureName, status.isOk());
            LOG.debug("liveness update status: {}", msg.body().encodePrettily());
        }

        livenessHandler.register(verticleName + "/" + instanceId + "/" + procedureName,
                statusFuture -> {

                    final Instant lastSeenEpochSecond = Instant.ofEpochSecond(Long.valueOf(lastSeen));
                    if (lastSeenEpochSecond.plusSeconds(LIVENESS_TIMEOUT_SECONDS).isBefore(Instant.now())) {
                        LOG.info("liveness check timed out for {}/{}/{}", verticleName, instanceId, procedureName);
                        status.setKO();
                    }

                    statusFuture.complete(status);
                }
                );
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

        final Future<Void> result = Future.future();
        if (router != null) {
            registerConsumers();

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

        if (readinessConsumer != null) {
            readinessConsumer.unregister();
            LOG.info("unregistered readiness consumer from event bus");
        }

        if (livenessConsumer != null) {
            livenessConsumer.unregister();
            LOG.info("unregistered liveness consumer from event bus");
        }

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
