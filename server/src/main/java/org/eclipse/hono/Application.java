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
package org.eclipse.hono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.registration.impl.BaseRegistrationAdapter;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointFactory;
import org.eclipse.hono.util.VerticleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * The Hono server main application class.
 * <p>
 * This class uses Spring Boot for configuring and wiring up Hono's components (Verticles).
 * By default there will be as many instances of each verticle created as there are CPU cores
 * available. The {@code hono.maxinstances} config property can be used to set the maximum number
 * of instances to create. This may be useful for executing tests etc.
 * </p>
 */
@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Value(value = "${hono.maxinstances:0}")
    private int maxInstances;
    @Value(value = "${hono.startuptimeout:20}")
    private int startupTimeout;
    @Autowired
    private Vertx vertx;
    @Autowired
    private VerticleFactory<TelemetryAdapter> adapterFactory;
    @Autowired
    private BaseRegistrationAdapter registration;
    @Autowired
    private VerticleFactory<AuthorizationService> authServiceFactory;
    @Autowired
    private VerticleFactory<HonoServer> serverFactory;
    @Autowired
    private List<EndpointFactory<?>> endpointFactories;
    private MessageConsumer<Object> restartListener;

    @PostConstruct
    public void registerVerticles() {

        final CountDownLatch startupLatch = new CountDownLatch(1);

        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        }
        final int instanceCount;
        if (maxInstances > 0 && maxInstances < Runtime.getRuntime().availableProcessors()) {
            instanceCount = maxInstances;
        } else {
            instanceCount = Runtime.getRuntime().availableProcessors();
        }

        Future<Void> started = Future.future();
        started.setHandler(ar -> {
            if (ar.failed()) {
                LOG.error("cannot start up HonoServer", ar.cause());
                shutdown();
            } else {
                startupLatch.countDown();
            }
        });

        CompositeFuture.all(deployVerticle(adapterFactory, instanceCount),
                deployVerticle(authServiceFactory, instanceCount),
                deployRegistrationService()).setHandler(ar -> {
            if (ar.succeeded()) {
                deployServer(instanceCount, started);
            } else {
                started.fail(ar.cause());
            }
        });

        restartListener = vertx.eventBus().consumer(Constants.APPLICATION_ENDPOINT).handler(message -> {
            JsonObject json = (JsonObject) message.body();
            String action = json.getString(Constants.APP_PROPERTY_ACTION);
            if (Constants.ACTION_RESTART.equals(action)) {
                LOG.info("restarting Hono...");
                vertx.eventBus().close(closeHandler -> {
                    List<Future> results = new ArrayList<>();
                    vertx.deploymentIDs().forEach(id -> {
                        Future<Void> result = Future.future();
                        vertx.undeploy(id, result.completer());
                        results.add(result);
                    });

                    CompositeFuture.all(results).setHandler(ar -> {
                        registerVerticles();
                    });
                });
            } else {
                LOG.warn("received unknown application action [{}], ignoring...", action);
            }
        });

        try {
            if (startupLatch.await(startupTimeout, TimeUnit.SECONDS)) {
                LOG.info("Hono startup completed successfully");
            } else {
                LOG.error("startup timed out after {} seconds, shutting down ...", startupTimeout);
                shutdown();
            }
        } catch (InterruptedException e) {
            LOG.error("startup process has been interrupted, shutting down ...");
            Thread.currentThread().interrupt();
            shutdown();
        }
    }

    private <T extends Verticle> Future<?> deployVerticle(VerticleFactory<T> factory, int instanceCount) {
        LOG.info("Starting component {}", factory);
        @SuppressWarnings("rawtypes")
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            Future<String> result = Future.future();
            vertx.deployVerticle(factory.newInstance(i, instanceCount), result.completer());
            results.add(result);
        }
        return CompositeFuture.all(results);
    }

    private Future<String> deployRegistrationService() {
        LOG.info("Starting registration service {}", registration);
        Future<String> result = Future.future();
        vertx.deployVerticle(registration, result.completer());
        return result;
    }

    private void deployServer(final int instanceCount, Future<Void> startFuture) {
        @SuppressWarnings("rawtypes")
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            HonoServer server = serverFactory.newInstance(i, instanceCount);
            for (EndpointFactory<?> ef : endpointFactories) {
                server.addEndpoint(ef.newInstance(i, instanceCount));
            }
            Future<String> result = Future.future();
            vertx.deployVerticle(server, result.completer());
            results.add(result);
        }
        CompositeFuture.all(results).setHandler(ar -> {
           if (ar.failed()) {
              startFuture.fail(ar.cause());
           } else {
               startFuture.complete();
           }
        });
    }

    @PreDestroy
    public void shutdown() {
        this.shutdown(startupTimeout, succeeded -> {
            // do nothing
        });
    }

    public void shutdown(final long maxWaitTime, final Handler<Boolean> shutdownHandler) {

        try {
            final CountDownLatch latch = new CountDownLatch(1);
            if (vertx != null) {
                LOG.debug("shutting down Hono server...");
                if (restartListener != null) {
                    restartListener.unregister();
                }
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

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(Application.class, args);
    }
}
