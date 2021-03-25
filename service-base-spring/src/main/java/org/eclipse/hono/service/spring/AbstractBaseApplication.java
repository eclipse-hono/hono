/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.spring;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PreDestroy;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.NoopHealthCheckServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.cpu.CpuCoreSensor;

/**
 * A base class for implementing Spring Boot applications.
 * <p>
 * This class provides basic abstractions for dealing with configuration, Vert.x verticals and health check server.
 */
public abstract class AbstractBaseApplication implements ApplicationRunner {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationConfigProperties config = new ApplicationConfigProperties();
    private Vertx vertx;

    private HealthCheckServer healthCheckServer = new NoopHealthCheckServer();

    /**
     * Sets the Vert.x instance to deploy the service to.
     *
     * @param vertx The vertx instance.
     * @throws NullPointerException if vertx is {@code null}.
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Gets the Vert.x instance the service gets deployed to.
     *
     * @return The vertx instance.
     */
    protected final Vertx getVertx() {
        return vertx;
    }

    /**
     * Sets the application configuration properties to use for this service.
     *
     * @param config The properties.
     * @throws NullPointerException if the properties are {@code null}.
     */
    @Autowired(required = false)
    public final void setApplicationConfiguration(final ApplicationConfigProperties config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Gets the application configuration properties used for this service.
     *
     * @return The properties.
     */
    protected final ApplicationConfigProperties getConfig() {
        return config;
    }

    /**
     * Sets the health check server for this application.
     *
     * @param healthCheckServer The health check server.
     * @throws NullPointerException if healthCheckServer is {@code null}.
     */
    @Autowired(required = false)
    public final void setHealthCheckServer(final HealthCheckServer healthCheckServer) {
        this.healthCheckServer = Objects.requireNonNull(healthCheckServer);
    }

    /**
     * Allow the application to do a "pre-flight" check, before services are deployed.
     * <p>
     * Although the current implementation is empty, classes overriding this method must call the super method, as
     * future implementations may be different.
     *
     * @throws IllegalStateException May be thrown if the implementor considers the application in state that it cannot
     *             be started up.
     */
    protected void preFlightCheck() throws IllegalStateException {
    }

    /**
     * Starts up this application.
     * <p>
     * The start up process entails the following steps:
     * <ol>
     * <li>invoke {@link #deployVerticles()} to deploy all verticles that should be part of this application</li>
     * <li>invoke {@link #postDeployVerticles()} to perform any additional post deployment steps</li>
     * <li>start the health check server</li>
     * </ol>
     *
     * @param args The command line arguments provided to the application.
     */
    @Override
    public void run(final ApplicationArguments args) {

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        }

        preFlightCheck();

        if (log.isInfoEnabled()) {
            log.info("running on Java VM [version: {}, name: {}, vendor: {}, max memory: {}MB, processors: {}]",
                    System.getProperty("java.version"),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vm.vendor"),
                    Runtime.getRuntime().maxMemory() >> 20,
                    CpuCoreSensor.availableProcessors());
        }

        final int startupTimeoutSeconds = config.getStartupTimeout();
        log.info("Waiting {} seconds for components to start ...", startupTimeoutSeconds);

        final CompletableFuture<Void> started = new CompletableFuture<>();

        deployVerticles()
            .compose(s -> postDeployVerticles())
            .compose(s -> healthCheckServer.start())
            .onSuccess(started::complete)
            .onFailure(started::completeExceptionally);

        try {
            started.get(startupTimeoutSeconds, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            log.error("startup timed out after {} seconds, shutting down ...", startupTimeoutSeconds);
            shutdown();
        } catch (final InterruptedException e) {
            log.error("startup process has been interrupted, shutting down ...");
            Thread.currentThread().interrupt();
            shutdown();
        } catch (final ExecutionException e) {
            log.error("exception occurred during startup, shutting down ...", e);
            shutdown();
        }
    }

    /**
     * Deploy verticles required by this application.
     * <p>
     * This is a generic method, deploying all verticles that should be part of this application.
     * <p>
     * Although the current implementation only returns a succeeded future, overriding this method it is required to
     * call "super", in order to enable future changes.
     *
     * @return A future indicating success. Application start-up fails if the returned future fails.
     */
    protected Future<?> deployVerticles() {
        return Future.succeededFuture();
    }

    /**
     * Invoked after the application verticles have been deployed successfully.
     * <p>
     * May be overridden to provide additional startup logic.
     * <p>
     * This default implementation simply returns a succeeded future.
     *
     * @return A future indicating success. Application start-up fails if the returned future fails.
     */
    protected Future<?> postDeployVerticles() {
        return Future.succeededFuture();
    }

    /**
     * Stops this application in a controlled fashion.
     */
    @PreDestroy
    public final void shutdown() {
        final int shutdownTimeoutSeconds = config.getStartupTimeout();
        shutdown(shutdownTimeoutSeconds, succeeded -> {
            // do nothing
        });
    }

    /**
     * Stops this application in a controlled fashion.
     *
     * @param maxWaitTime The maximum time to wait for the server to shut down (in seconds).
     * @param shutdownHandler The handler to invoke with the result of the shutdown attempt.
     */
    public final void shutdown(final long maxWaitTime, final Handler<Boolean> shutdownHandler) {

        try {
            log.info("shutting down application...");

            preShutdown();
            final CountDownLatch latch = new CountDownLatch(1);

            stopHealthCheckServer().onComplete(result -> {

                if (vertx != null) {
                    log.info("closing vert.x instance ...");
                    vertx.close(r -> {
                        if (r.failed()) {
                            log.error("could not close vert.x instance", r.cause());
                        }
                        latch.countDown();
                    });
                } else {
                    latch.countDown(); // nothing to wait for
                }
            });

            if (latch.await(maxWaitTime, TimeUnit.SECONDS)) {
                log.info("application has been shut down successfully");
                shutdownHandler.handle(Boolean.TRUE);
            } else {
                log.error("shut down timed out, aborting...");
                shutdownHandler.handle(Boolean.FALSE);
            }
        } catch (final InterruptedException e) {
            log.error("application shut down has been interrupted, aborting...");
            Thread.currentThread().interrupt();
            shutdownHandler.handle(Boolean.FALSE);
        }
    }

    private Future<Void> stopHealthCheckServer() {
        return healthCheckServer.stop();
    }

    /**
     * Invoked before application shutdown is initiated.
     * <p>
     * May be overridden to provide additional shutdown handling, e.g. releasing resources before the vert.x instance is
     * closed.
     */
    protected void preShutdown() {
        // empty
    }

    /**
     * Registers additional health checks.
     *
     * @param provider The provider of the health checks.
     */
    protected final void registerHealthchecks(final HealthCheckProvider provider) {
        Optional.ofNullable(provider).ifPresent(p -> {
            log.debug("registering health checks [provider: {}]", p.getClass().getName());
            healthCheckServer.registerHealthCheckResources(p);
        });
    }
}
