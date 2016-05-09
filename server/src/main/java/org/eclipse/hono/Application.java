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

import javax.annotation.PostConstruct;

import org.eclipse.hono.authorization.impl.BaseAuthorizationService;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.telemetry.impl.BaseTelemetryAdapter;
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
    private Vertx                    vertx;
    @Autowired
    private BaseTelemetryAdapter     adapter;
    @Autowired
    private BaseAuthorizationService authService;
    @Autowired
    private HonoServer               server;

    @PostConstruct
    public void registerVerticles() {
        vertx.deployVerticle(adapter);
        vertx.deployVerticle(authService);
        vertx.deployVerticle(server);
    }

    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        SpringApplication.run(Application.class, args);
    }
}
