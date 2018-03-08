/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PreDestroy;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * A base class for implementing Spring Boot applications.
 * <p>
 * This class requires that an instance of {@link ObjectFactory} is provided
 * ({@link #addServiceFactories(Set)} for each service to be exposed by this application.
 */
public class AbstractApplication implements ApplicationRunner {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final Set<ObjectFactory<? extends AbstractServiceBase<?>>> serviceFactories = new HashSet<>();
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
    public final void addServiceFactories(final Set<ObjectFactory<? extends AbstractServiceBase<?>>> factories) {
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
     * <p>
     * The start up process entails the following steps:
     * <ol>
     * <li>invoke <em>deployRequiredVerticles</em> to deploy the verticle(s) implementing the service's functionality</li>
     * <li>invoke <em>deployServiceVerticles</em> to deploy the protocol specific service endpoints</li>
     * <li>invoke <em>postRegisterServiceVerticles</em> to perform any additional post deployment steps</li>
     * <li>start the health check server</li>
     * </ol>
     * 
     * @param args The command line arguments provided to the application.
     */
    public void run(final ApplicationArguments args) {

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        } else if (serviceFactories.isEmpty()) {
            throw new IllegalStateException("no service factory has been configured");
        }

        healthCheckServer = new HealthCheckServer(vertx, config);

        final Future<Void> future = deployRequiredVerticles(config.getMaxInstances())
             .compose(s -> deployServiceVerticles())
             .compose(s -> postRegisterServiceVerticles())
             .compose(s -> healthCheckServer.start());

        final CompletableFuture<Void> started = new CompletableFuture<>();
        future.setHandler(result -> {
            if (result.failed()) {
                started.completeExceptionally(result.cause());
            } else {
                started.complete(null);
            }
        });

        final int startupTimeoutSeconds = config.getStartupTimeout();

        try {
            log.debug("Waiting for {} seconds to start up", startupTimeoutSeconds);
            started.get(startupTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("startup timed out after {} seconds, shutting down ...", startupTimeoutSeconds);
            shutdown();
        } catch (InterruptedException e) {
            log.error("startup process has been interrupted, shutting down ...");
            Thread.currentThread().interrupt();
            shutdown();
        } catch (ExecutionException e) {
            log.error("exception occurred during startup, shutting down ...", e);
            shutdown();
        }
    }

    private CompositeFuture deployServiceVerticles() {
        final int maxInstances = config.getMaxInstances();

        @SuppressWarnings("rawtypes")
        final List<Future> deploymentTracker = new ArrayList<>();

        for (ObjectFactory<? extends AbstractServiceBase<?>> serviceFactory : serviceFactories) {

            AbstractServiceBase<?> serviceInstance = serviceFactory.getObject();

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
            log.info("shutting down application...");

            preShutdown();
            final CountDownLatch latch = new CountDownLatch(1);

            stopHealthCheckServer().setHandler(result -> {

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
        } catch (InterruptedException e) {
            log.error("application shut down has been interrupted, aborting...");
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
     * releasing resources before the vert.x instance is closed.
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
        healthCheckServer.registerHealthCheckResources(provider);
    }
}
