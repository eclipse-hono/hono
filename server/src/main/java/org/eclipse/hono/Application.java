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

import java.util.List;

import javax.annotation.PostConstruct;

import org.eclipse.hono.authorization.AuthorizationService;
import org.eclipse.hono.registration.impl.BaseRegistrationAdapter;
import org.eclipse.hono.server.Endpoint;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.server.HonoServerFactory;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.util.ComponentFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * The Spring Boot main application class.
 *
 */
@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {

    @Autowired
    private Vertx                                  vertx;
    @Autowired
    private ComponentFactory<TelemetryAdapter>     adapterFactory;
    @Autowired
    private BaseRegistrationAdapter                registration;
    @Autowired
    private ComponentFactory<AuthorizationService> authServiceFactory;
    @Autowired
    private HonoServerFactory                      serverFactory;
    @Autowired
    private List<ComponentFactory<Endpoint>>       endpointFactories;

    @PostConstruct
    public void registerVerticles() {
        if (vertx == null) {
            throw new IllegalStateException("no Vert.x instance has been configured");
        }
        int instanceCount = Runtime.getRuntime().availableProcessors();
        deployTelemetryAdapter(instanceCount);
        deployAuthorizationService(instanceCount);
        deployServer(instanceCount);
        vertx.deployVerticle(registration);
    }

    private void deployAuthorizationService(final int instanceCount) {
        for (int i = 1; i <= instanceCount; i++) {
            vertx.deployVerticle(authServiceFactory.newInstance(i, instanceCount));
        }
    }

    private void deployTelemetryAdapter(final int instanceCount) {
        for (int i = 1; i <= instanceCount; i++) {
            vertx.deployVerticle(adapterFactory.newInstance(i, instanceCount));
        }
    }

    private void deployServer(final int instanceCount) {
        for (int i = 1; i <= instanceCount; i++) {
            HonoServer server = serverFactory.newInstance(i, instanceCount);
            for (ComponentFactory<Endpoint> ef : endpointFactories) {
                server.addEndpoint(ef.newInstance(i, instanceCount));
            }
            vertx.deployVerticle(server);
        }
    }

    public static void main(final String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(Application.class, args);
    }
}
