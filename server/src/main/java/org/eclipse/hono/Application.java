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

import javax.annotation.PostConstruct;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.spi.FutureFactory;
import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.registration.impl.BaseRegistrationAdapter;
import org.eclipse.hono.server.Endpoint;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.server.HonoServerFactory;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.util.ComponentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Value(value = "${hono.maxinstances:0}")
    private int maxInstances;
    @Autowired
    private Vertx vertx;
    @Autowired
    private ComponentFactory<TelemetryAdapter> adapterFactory;
    @Autowired
    private BaseRegistrationAdapter registration;
    @Autowired
    private ComponentFactory<AuthorizationService> authServiceFactory;
    @Autowired
    private HonoServerFactory serverFactory;
    @Autowired
    private List<ComponentFactory<Endpoint>> endpointFactories;

    @PostConstruct
    public void registerVerticles() throws Exception {
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
                vertx.close();
            }
        });
        CompositeFuture.all(deployComponent(adapterFactory, instanceCount),
                deployComponent(authServiceFactory, instanceCount),
                deployRegistrationService()).setHandler(ar -> {
            if (ar.succeeded()) {
                deployServer(instanceCount, started);
            } else {
                LOG.error("Cannot start up HonoServer", ar.cause());
                started.fail(ar.cause());
            }
        });

    }

    private Future deployComponent(ComponentFactory factory, int instanceCount) throws Exception {
        LOG.info("Staring component {}", factory);
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            Future result = Future.future();
            vertx.deployVerticle((Verticle) factory.newInstance(i, instanceCount), result.completer());
            results.add(result);
        }
        return CompositeFuture.all(results);
    }

    private Future deployRegistrationService() {
        LOG.info("Starting registration service {}", registration);
        Future result = Future.future();
        vertx.deployVerticle(registration, result.completer());
        return result;
    }

    private void deployServer(final int instanceCount, Future startFuture) {
        List<Future> results = new ArrayList<>();
        for (int i = 1; i <= instanceCount; i++) {
            HonoServer server = serverFactory.newInstance(i, instanceCount);
            for (ComponentFactory<Endpoint> ef : endpointFactories) {
                server.addEndpoint(ef.newInstance(i, instanceCount));
            }
            Future result = Future.future();
            vertx.deployVerticle(server, result.completer());
            results.add(result);
        }
        CompositeFuture.all(results).setHandler(ar -> {
            if (ar.failed()) {
                startFuture.fail(ar.cause());
            }
        });
    }

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(Application.class, args);
    }
}
