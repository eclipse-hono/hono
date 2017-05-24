/**
 * Copyright (c) 2016,2017 Bosch Software Innovations GmbH.
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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.server.HonoServerFactory;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.AuthorizationService;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * The Hono server main application class.
 * <p>
 * This class configures and wires up Hono's components (Verticles).
 * By default there will be as many instances of each verticle created as there are CPU cores
 * available. The {@code hono.maxinstances} config property can be used to set the maximum number
 * of instances to create. This may be useful for executing tests etc.
 * </p>
 */
public class HonoApplication {

    private static final Logger LOG = LoggerFactory.getLogger(HonoApplication.class);

    private ServiceConfigProperties honoConfig;
    private Vertx vertx;
    private RegistrationService registrationService;
    private CredentialsService credentialsService;
    private AuthenticationService authenticationService;
    private AuthorizationService authorizationService;
    private HonoServerFactory serverFactory;

    /**
     * @param honoConfig the honoConfig to set
     */
    @Autowired
    public final void setHonoConfig(final ServiceConfigProperties honoConfig) {
        this.honoConfig = honoConfig;
    }

    public final ServiceConfigProperties getHonoConfig() {
        return honoConfig;
    }

    /**
     * @param vertx the vertx to set
     */
    @Autowired
    public final void setVertx(final Vertx vertx) {
        this.vertx = vertx;
    }

    public final Vertx getVertx() {
        return vertx;
    }

    /**
     * @param credentialsService the credentialsService to set
     */
    @Autowired
    public final void setCredentialsService(final CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public final CredentialsService getCredentialsService() {
        return credentialsService;
    }

    /**
     * @param registrationService the registrationService to set
     */
    @Autowired
    public final void setRegistrationService(final RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    public final RegistrationService getRegistrationService() {
        return registrationService;
    }

    /**
     * @param authenticationService the authenticationService to set
     */
    @Autowired
    public final void setAuthenticationService(final AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public final AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    /**
     * @param authorizationService the authorizationService to set
     */
    @Autowired
    public final void setAuthorizationService(final AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public final AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    /**
     * @param serverFactory the serverFactory to set
     */
    @Autowired
    public final void setServerFactory(final HonoServerFactory serverFactory) {
        this.serverFactory = serverFactory;
    }

    public final HonoServerFactory getServerFactory() {
        return serverFactory;
    }

    /**
     * Starts the Hono server.
     */
    @PostConstruct
    public final void startup() {
        boolean startupSuccessful = registerVerticles();
        if (startupSuccessful) {
            postRegisterVerticles();
        }
    }
    
    /**
     * Deploys all verticles the Hono server consists of.
     * 
     * @return true if deployment was successful
     */
    private boolean registerVerticles() {

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        }

        final CountDownLatch startupLatch = new CountDownLatch(1);
        final AtomicBoolean startupSucceeded = new AtomicBoolean(false);

        // without creating a first instance here, deployment of the HonoServer verticles fails
        // TODO: find out why
        HonoServer firstInstance = serverFactory.getHonoServer();

        final int instanceCount = honoConfig.getMaxInstances();

        Future<Void> started = Future.future();
        started.setHandler(ar -> {
            if (ar.failed()) {
                LOG.error("cannot start up HonoServer", ar.cause());
            } else {
                startupSucceeded.set(true);
            }
            startupLatch.countDown();
        });

        CompositeFuture.all(
                deployAuthenticationService(), // we only need 1 authentication service
                deployAuthorizationService(), // we only need 1 authorization service
                deployCredentialsService(),
                deployRegistrationService()).setHandler(ar -> {
            if (ar.succeeded()) {
                deployServer(firstInstance, instanceCount, started);
            } else {
                started.fail(ar.cause());
            }
        });

        try {
            if (startupLatch.await(honoConfig.getStartupTimeout(), TimeUnit.SECONDS)) {
                if (startupSucceeded.get()) {
                    LOG.info("Hono startup completed successfully");
                } else {
                    shutdown();
                }
            } else {
                LOG.error("startup timed out after {} seconds, shutting down ...", honoConfig.getStartupTimeout());
                shutdown();
                startupSucceeded.set(false);
            }
        } catch (InterruptedException e) {
            LOG.error("startup process has been interrupted, shutting down ...");
            Thread.currentThread().interrupt();
            shutdown();
            startupSucceeded.set(false);
        }
        return startupSucceeded.get();
    }

    /**
     * Invoked after the Hono verticles have been registered as part of Hono startup.
     * May be overridden to provide additional startup logic.
     */
    protected void postRegisterVerticles() {
        // empty
    }

    private Future<String> deployRegistrationService() {
        LOG.info("Starting registration service {}", registrationService);
        Future<String> result = Future.future();
        vertx.deployVerticle(registrationService, result.completer());
        return result;
    }

    private Future<String> deployCredentialsService() {
        LOG.info("Starting credentials service {}", credentialsService);
        Future<String> result = Future.future();
        vertx.deployVerticle(credentialsService, result.completer());
        return result;
    }

    private Future<String> deployAuthenticationService() {
        LOG.info("Starting authentication service {}", authenticationService);
        Future<String> result = Future.future();
        vertx.deployVerticle(authenticationService, result.completer());
        return result;
    }

    private Future<String> deployAuthorizationService() {
        LOG.info("Starting authorization service {}", authorizationService);
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
    public final void shutdown() {
        shutdown(honoConfig.getStartupTimeout(), succeeded -> {
            // do nothing
        });
    }

    /**
     * Stops the Hono server in a controlled fashion.
     * 
     * @param maxWaitTime The maximum time to wait for the server to shut down (in seconds).
     * @param shutdownHandler The handler to invoke with the result of the shutdown attempt.
     */
    public final void shutdown(final long maxWaitTime, final Handler<Boolean> shutdownHandler) {

        try {
            preShutdown();
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
     * Invoked before Hono shutdown is initiated.
     * May be overridden to provide additional shutdown handling.
     */
    protected void preShutdown() {
        // empty
    }
}
