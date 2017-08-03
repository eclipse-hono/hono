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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import io.vertx.core.*;

/**
 * A base class for implementing Spring Boot applications.
 * <p>
 * This class requires that an instance of {@link ObjectFactory<AbstractServiceBase>} is provided
 * ({@link #addServiceFactories(Set)} for each service to be exposed by this application.
 */
public class AbstractApplication implements ApplicationRunner {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Set<ObjectFactory<? extends AbstractServiceBase>> serviceFactories = new HashSet<>();
    private ApplicationConfigProperties config = new ApplicationConfigProperties();
    private Vertx vertx;

    private HealthCheckServer healthCheckServer;

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
    protected Vertx getVertx() {
        return vertx;
    }

    /**
     * Adds the factories to use for creating service instances to
     * deploy the Vert.x container during startup.
     * <p>
     * 
     * @param factories The service factories.
     * @throws NullPointerException if factories is {@code null}.
     */
    @Autowired
    public final void addServiceFactories(final Set<ObjectFactory<? extends AbstractServiceBase>> factories) {
        Objects.requireNonNull(factories);
        serviceFactories.addAll(factories);
        log.debug("added {} service factories", factories.size());
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
     * Starts up this application.
     */
    public void run(final ApplicationArguments args) throws Exception {

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        } else if (serviceFactories.isEmpty()) {
            throw new IllegalStateException("no service factory has been configured");
        }

        healthCheckServer = new HealthCheckServer(vertx, config);

        final CountDownLatch startupLatch = new CountDownLatch(1);
        final int startupTimeoutSeconds = config.getStartupTimeout();

        Future<Void> started = Future.future();
        started.setHandler(s -> {
            if (s.failed()) {
                log.error("cannot start up application", s.cause());
            } else {
                startupLatch.countDown();
            }
        });

        deployRequiredVerticles(config.getMaxInstances())
            .compose(s -> deployServiceVerticles())
            .compose(s -> postRegisterServiceVerticles())
            .compose(s -> healthCheckServer.start())
            .compose(s -> started.complete(), started);

        try {
            if (startupLatch.await(startupTimeoutSeconds, TimeUnit.SECONDS)) {
                log.info("application startup completed successfully");
            } else {
                log.error("startup timed out after {} seconds, shutting down ...", startupTimeoutSeconds);
                shutdown();
            }
        } catch (InterruptedException e) {
            log.error("startup process has been interrupted, shutting down ...");
            Thread.currentThread().interrupt();
            shutdown();
        }
    }

    private CompositeFuture deployServiceVerticles() {
        final int maxInstances = config.getMaxInstances();

        final List<Future> deploymentTracker = new ArrayList<>();

        for (ObjectFactory<? extends AbstractServiceBase> serviceFactory : serviceFactories) {

            AbstractServiceBase serviceInstance = serviceFactory.getObject();

            healthCheckServer.registerHealthCheckResources(serviceInstance);

            final Future<String> deployTracker = Future.future();
            vertx.deployVerticle(serviceInstance, deployTracker.completer());
            deploymentTracker.add(deployTracker);

            for (int i = 1; i < maxInstances; i++) { // first instance has already been deployed
                serviceInstance = serviceFactory.getObject();
                log.debug("created new instance of service: {}", serviceInstance);
                final Future<String> tracker = Future.future();
                vertx.deployVerticle(serviceInstance, tracker.completer());
                deploymentTracker.add(tracker);
            }
        }

        return CompositeFuture.all(deploymentTracker);
    }

    /**
     * Invoked before the service instances are being deployed.
     * <p>
     * May be overridden to prepare the environment for the service instances,
     * e.g. deploying additional (prerequisite) verticles.
     * <p>
     * This default implementation simply returns a succeeded future.
     * 
     * @param maxInstances The number of service verticle instances to deploy.
     * @return A future indicating success. Application start-up fails if the
     *         returned future fails.
     */
    protected Future<Void> deployRequiredVerticles(final int maxInstances) {
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
            preShutdown();
            final CountDownLatch latch = new CountDownLatch(1);

            stopHealthCheckServer().setHandler(result -> {
                if (vertx != null) {
                    log.debug("shutting down application...");
                    vertx.close(r -> {
                        if (r.failed()) {
                            log.error("could not shut down application cleanly", r.cause());
                        }
                        latch.countDown();
                    });
                } else {
                    latch.countDown(); // Don't wait for the timeout.
                }
            });

            if (latch.await(maxWaitTime, TimeUnit.SECONDS)) {
                log.info("application has been shut down successfully");
                shutdownHandler.handle(Boolean.TRUE);
            } else {
                log.error("shut down timed out, aborting...");
                shutdownHandler.handle(Boolean.FALSE);
            }
        } catch (InterruptedException e) {
            log.error("shut down has been interrupted, aborting...");
            Thread.currentThread().interrupt();
            shutdownHandler.handle(Boolean.FALSE);
        }
    }

    private Future<Void> stopHealthCheckServer() {
        if (healthCheckServer != null) {
            return healthCheckServer.stop();
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Invoked after the service instances have been deployed successfully.
     * <p>
     * May be overridden to provide additional startup logic, e.g. deploying
     * additional verticles.
     * <p>
     * This default implementation simply returns a succeeded future.
     * 
     * @return A future indicating success. Application start-up fails if the
     *         returned future fails.
     */
    protected Future<Void> postRegisterServiceVerticles() {
        return Future.succeededFuture();
    }

    /**
     * Invoked before application shutdown is initiated.
     * <p>
     * May be overridden to provide additional shutdown handling, e.g.
     * releasing resources.
     */
    protected void preShutdown() {
        // empty
    }
}
