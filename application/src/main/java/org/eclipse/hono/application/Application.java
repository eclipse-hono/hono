/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.authentication.AuthenticationService;
import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.registration.RegistrationService;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.server.HonoServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * The Hono server main application class.
 * <p>
 * This class uses Spring Boot for configuring and wiring up Hono's components (Verticles).
 * By default there will be as many instances of each verticle created as there are CPU cores
 * available. The {@code hono.maxinstances} config property can be used to set the maximum number
 * of instances to create. This may be useful for executing tests etc.
 * </p>
 */
@ComponentScan(basePackages = "org.eclipse.hono")
@Configuration
@EnableAutoConfiguration
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private HonoConfigProperties honoConfig;
    private Vertx vertx;
    private RegistrationService registrationService;
    private AuthenticationService authenticationService;
    private AuthorizationService authorizationService;
    private HonoServerFactory serverFactory;

    /**
     * @param honoConfig the honoConfig to set
     */
    @Autowired
    public void setHonoConfig(final HonoConfigProperties honoConfig) {
        this.honoConfig = honoConfig;
    }

    /**
     * @param vertx the vertx to set
     */
    @Autowired
    public void setVertx(final Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * @param registrationService the registrationService to set
     */
    @Autowired
    public void setRegistrationService(final RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    @Autowired
    public void setAuthenticationService(final AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * @param authorizationService the authorizationService to set
     */
    @Autowired
    public void setAuthorizationService(final AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * @param serverFactory the serverFactory to set
     */
    @Autowired
    public void setServerFactory(final HonoServerFactory serverFactory) {
        this.serverFactory = serverFactory;
    }

    /**
     * Deploys all verticles the Hono server consists of.
     */
    @PostConstruct
    public void registerVerticles() {

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        }

        final CountDownLatch startupLatch = new CountDownLatch(1);

        // without creating a first instance here, deployment of the HonoServer verticles fails
        // TODO: find out why
        HonoServer firstInstance = serverFactory.getHonoServer();

        final int instanceCount = honoConfig.getMaxInstances();

        Future<Void> started = Future.future();
        started.setHandler(ar -> {
            if (ar.failed()) {
                LOG.error("cannot start up HonoServer", ar.cause());
                shutdown();
            } else {
                startupLatch.countDown();
            }
        });

        CompositeFuture.all(
                deployAuthenticationService(), // we only need 1 authentication service
                deployAuthorizationService(), // we only need 1 authorization service
                deployRegistrationService()).setHandler(ar -> {
            if (ar.succeeded()) {
                deployServer(firstInstance, instanceCount, started);
            } else {
                started.fail(ar.cause());
            }
        });

        try {
            if (startupLatch.await(honoConfig.getStartupTimeout(), TimeUnit.SECONDS)) {
                LOG.info("Hono startup completed successfully");
            } else {
                LOG.error("startup timed out after {} seconds, shutting down ...", honoConfig.getStartupTimeout());
                shutdown();
            }
        } catch (InterruptedException e) {
            LOG.error("startup process has been interrupted, shutting down ...");
            Thread.currentThread().interrupt();
            shutdown();
        }
    }

    private Future<String> deployRegistrationService() {
        LOG.info("Starting registration service {}", registrationService);
        Future<String> result = Future.future();
        vertx.deployVerticle(registrationService, result.completer());
        return result;
    }

    private Future<String> deployAuthenticationService() {
        LOG.info("Starting authentication service {}", authenticationService);
        Future<String> result = Future.future();
        vertx.deployVerticle(authenticationService, result.completer());
        return result;
    }

    private Future<String> deployAuthorizationService() {
        LOG.info("Starting authorizaion service {}", authorizationService);
        Future<String> result = Future.future();
        vertx.deployVerticle(authorizationService, result.completer());
        return result;
    }

    private void deployServer(final HonoServer firstInstance, final int instanceCount, Future<Void> startFuture) {
        @SuppressWarnings("rawtypes")
        List<Future> results = new ArrayList<>();
        deployServerInstance(firstInstance, results);
        for (int i = 1; i < instanceCount; i++) {
            HonoServer server = serverFactory.getHonoServer();
            deployServerInstance(server, results);
        }
        CompositeFuture.all(results).setHandler(ar -> {
           if (ar.failed()) {
              startFuture.fail(ar.cause());
           } else {
               startFuture.complete();
           }
        });
    }

    @SuppressWarnings("rawtypes")
    private void deployServerInstance(final HonoServer instance, final List<Future> deployResults) {
        Future<String> result = Future.future();
        vertx.deployVerticle(instance, result.completer());
        deployResults.add(result);
    }

    /**
     * Stops the Hono server in a controlled fashion.
     */
    @PreDestroy
    public void shutdown() {
        this.shutdown(honoConfig.getStartupTimeout(), succeeded -> {
            // do nothing
        });
    }

    /**
     * Stops the Hono server in a controlled fashion.
     * 
     * @param maxWaitTime The maximum time to wait for the server to shut down (in seconds).
     * @param shutdownHandler The handler to invoke with the result of the shutdown attempt.
     */
    public void shutdown(final long maxWaitTime, final Handler<Boolean> shutdownHandler) {

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            if (vertx != null) {
                LOG.debug("shutting down Hono server...");
                vertx.close(r -> {
                    if (r.failed()) {
                        LOG.error("could not shut down Hono cleanly", r.cause());
                    }
                    latch.countDown();
                });
            }
            if (latch.await(maxWaitTime, TimeUnit.SECONDS)) {
                LOG.info("Hono server has been shut down successfully");
                shutdownHandler.handle(Boolean.TRUE);
            } else {
                LOG.error("shut down of Hono server timed out, aborting...");
                shutdownHandler.handle(Boolean.FALSE);
            }
        } catch (InterruptedException e) {
            LOG.error("shut down of Hono server has been interrupted, aborting...");
            Thread.currentThread().interrupt();
            shutdownHandler.handle(Boolean.FALSE);
        }
    }

    /**
     * Starts the Hono server.
     * 
     * @param args command line arguments to pass to Hono.
     */
    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
