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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.*;

/**
 * A base class for implementing Spring Boot applications.
 * <p>
 * This class supports the configuration of an {@code ObjectFactory} for creating instances
 * of the service implementation that this application exposes.
 * 
 * @param <S> The service type.
 * @param <C> The type of configuration this application uses.
 */
// TODO: remove parameter C.
public class AbstractApplication<S, C extends ServiceConfigProperties> {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private ObjectFactory<S> serviceFactory;
    private ApplicationConfigProperties config = new ApplicationConfigProperties();
    private Vertx vertx;

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
     * Sets the factory to use for creating service instances to
     * deploy the Vert.x container during startup.
     * <p>
     * Start up of the application will fail if the service instances
     * produced by the factory are not {@code Verticle}s.
     * 
     * @param factory The factory.
     * @throws NullPointerException if the factory is {@code null}.
     */
    @Autowired
    public final void setServiceFactory(final ObjectFactory<S> factory) {
        this.serviceFactory = Objects.requireNonNull(factory);
        log.debug("using service factory: " + factory.getClass().getName());
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
    @PostConstruct
    public final void startup() {

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        } else if (serviceFactory == null) {
            throw new IllegalStateException("no service factory has been configured");
        }

        final CountDownLatch startupLatch = new CountDownLatch(1);
        final int maxInstances = config.getMaxInstances();
        final int startupTimeoutSeconds = config.getStartupTimeout();
        final S firstServiceInstance = serviceFactory.getObject();

        Future<Void> started = Future.future();
        started.setHandler(s -> {
            if (s.failed()) {
                log.error("cannot start up application", s.cause());
            } else {
                startupLatch.countDown();
            }
        });

        deployRequiredVerticles(maxInstances)
            .compose(s -> deployServiceVerticles(firstServiceInstance, maxInstances - 1))
            .compose(s -> postRegisterServiceVerticles())
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

    final Future<Void> deployServiceVerticles(final S firstServiceInstance, final int maxInstances) {

        Objects.requireNonNull(firstServiceInstance);
        final Future<Void> result = Future.future();
        @SuppressWarnings("rawtypes")
        final List<Future> deploymentTracker = new ArrayList<>();
        if (!Verticle.class.isInstance(firstServiceInstance)) {
            result.fail("service class is not a Verticle");
        } else {
            customizeServiceInstance(firstServiceInstance);
            final Future<String> deployTracker = Future.future();
            vertx.deployVerticle((Verticle) firstServiceInstance, deployTracker.completer());
            deploymentTracker.add(deployTracker);

            for (int i = 0; i < maxInstances; i++) {
                final S serviceInstance = serviceFactory.getObject();
                log.debug("created new instance of service: " + serviceInstance);
                if (Verticle.class.isInstance(serviceInstance)) {
                    customizeServiceInstance(serviceInstance);
                    final Future<String> tracker = Future.future();
                    vertx.deployVerticle((Verticle) serviceInstance, tracker.completer());
                    deploymentTracker.add(tracker);
                }
            }

            CompositeFuture.all(deploymentTracker).setHandler(s -> {
                if (s.succeeded()) {
                    result.complete();
                } else {
                    result.fail(s.cause());
                }
            });
        }

        return result;
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
            if (vertx != null) {
                log.debug("shutting down application...");
                vertx.close(r -> {
                    if (r.failed()) {
                        log.error("could not shut down application cleanly", r.cause());
                    }
                    latch.countDown();
                });
            }
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

    /**
     * Invoked with each created service instance.
     * <p>
     * Subclasses may override this method in order to customize
     * the service instance before it gets deployed to the Vert.x
     * container.
     * 
     * @param instance The service instance.
     */
    protected void customizeServiceInstance(final S instance) {
        // empty
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
